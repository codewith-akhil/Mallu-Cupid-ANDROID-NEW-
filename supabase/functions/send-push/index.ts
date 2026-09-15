// =============================================================================
// Mallu Cupid — send-push Edge Function
// =============================================================================
// Sends a push notification to a user via Firebase Cloud Messaging (FCM).
// Uses the FCM Legacy HTTP API (requires FCM_SERVER_KEY env var).
//
// POST /functions/v1/send-push
// Body: {
//   "user_id": "uuid",
//   "type": "match" | "message" | "super_like" | "message_like" | "promo",
//   "title": "You have a new match!",
//   "body": "You and Remy liked each other 💘",
//   "data": { "match_id": "uuid", "profile_id": "uuid" }
// }
// =============================================================================

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_ROLE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const FCM_SERVER_KEY = Deno.env.get("FCM_SERVER_KEY");

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

function json(body: Record<string, unknown>, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...CORS },
  });
}

async function getUserFcmToken(userId: string): Promise<string | null> {
  const res = await fetch(
    `${SUPABASE_URL}/rest/v1/profiles?id=eq.${userId}&select=fcm_token`,
    {
      headers: {
        apikey: SERVICE_ROLE,
        Authorization: `Bearer ${SERVICE_ROLE}`,
      },
    }
  );
  if (!res.ok) return null;
  const data = await res.json();
  return data?.[0]?.fcm_token ?? null;
}

async function logNotification(userId: string, type: string, title: string, body: string, data: object) {
  try {
    await fetch(`${SUPABASE_URL}/rest/v1/push_notifications_log`, {
      method: "POST",
      headers: {
        apikey: SERVICE_ROLE,
        Authorization: `Bearer ${SERVICE_ROLE}`,
        "Content-Type": "application/json",
        Prefer: "return=minimal",
      },
      body: JSON.stringify({ user_id: userId, type, title, body, data }),
    });
  } catch {}
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);

  let payload: any;
  try {
    payload = await req.json();
  } catch {
    return json({ error: "Invalid JSON body" }, 400);
  }

  const { user_id, type, title, body, data } = payload;
  if (!user_id) return json({ error: "user_id is required" }, 400);
  if (!type) return json({ error: "type is required" }, 400);

  // 1. Get the user's FCM token
  const fcmToken = await getUserFcmToken(user_id);
  if (!fcmToken) {
    return json({ ok: false, reason: "no_fcm_token" }, 200);
  }

  // 2. Check if FCM server key is configured
  if (!FCM_SERVER_KEY) {
    return json({ ok: false, reason: "fcm_not_configured" }, 200);
  }

  // 3. Send via FCM Legacy HTTP API
  const fcmRes = await fetch("https://fcm.googleapis.com/fcm/send", {
    method: "POST",
    headers: {
      Authorization: `key=${FCM_SERVER_KEY}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      to: fcmToken,
      notification: { title: title ?? "Mallu Cupid", body: body ?? "", sound: "default" },
      data: { type, title: title ?? "", body: body ?? "", ...(data ?? {}) },
      priority: "high",
    }),
  });

  const fcmResult = await fcmRes.json();

  // 4. Log the notification
  await logNotification(user_id, type, title ?? "", body ?? "", data ?? {});

  return json({ ok: true, fcm_result: fcmResult });
});
