// =============================================================================
// Mallu Cupid — send-push Edge Function (FCM HTTP v1 API)
// =============================================================================
// Sends push notifications via Firebase Cloud Messaging HTTP v1 API
// using a Google Cloud service account JWT (no legacy server key needed).
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
const FCM_SERVICE_ACCOUNT_JSON = Deno.env.get("FCM_SERVICE_ACCOUNT_JSON");

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
      headers: { apikey: SERVICE_ROLE, Authorization: `Bearer ${SERVICE_ROLE}` },
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

// --- JWT creation for FCM HTTP v1 API ---

function base64url(input: Uint8Array | string): string {
  const data = typeof input === "string" ? new TextEncoder().encode(input) : input;
  const b64 = btoa(String.fromCharCode(...data));
  return b64.replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

async function createJwt(serviceAccount: any): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = { alg: "RS256", typ: "JWT" };
  const payload = {
    iss: serviceAccount.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  };

  const headerB64 = base64url(JSON.stringify(header));
  const payloadB64 = base64url(JSON.stringify(payload));
  const signingInput = `${headerB64}.${payloadB64}`;

  // Import the private key
  const privateKeyPem = serviceAccount.private_key
    .replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "")
    .replace(/\n/g, "");

  const privateKeyDer = Uint8Array.from(atob(privateKeyPem), c => c.charCodeAt(0));

  const cryptoKey = await crypto.subtle.importKey(
    "pkcs8",
    privateKeyDer.buffer,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  );

  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    cryptoKey,
    new TextEncoder().encode(signingInput)
  );

  const signatureB64 = base64url(new Uint8Array(signature));
  return `${signingInput}.${signatureB64}`;
}

async function getAccessToken(serviceAccount: any): Promise<string> {
  const jwt = await createJwt(serviceAccount);
  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${jwt}`,
  });
  const data = await res.json();
  return data.access_token;
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

  // 2. Check if service account is configured
  if (!FCM_SERVICE_ACCOUNT_JSON) {
    return json({ ok: false, reason: "fcm_not_configured" }, 200);
  }

  try {
    const serviceAccount = JSON.parse(FCM_SERVICE_ACCOUNT_JSON);
    const projectId = serviceAccount.project_id;

    // 3. Get OAuth2 access token
    const accessToken = await getAccessToken(serviceAccount);

    // 4. Send via FCM HTTP v1 API
    const fcmRes = await fetch(
      `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`,
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${accessToken}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          message: {
            token: fcmToken,
            notification: {
              title: title ?? "Mallu Cupid",
              body: body ?? "",
            },
            data: {
              type: type,
              title: title ?? "",
              body: body ?? "",
              ...(data ?? {}),
            },
            android: {
              priority: "high",
              notification: {
                sound: "default",
                click_action: "FLUTTER_NOTIFICATION_CLICK",
              },
            },
          },
        }),
      }
    );

    const fcmResult = await fcmRes.json();

    // 5. Log the notification
    await logNotification(user_id, type, title ?? "", body ?? "", data ?? {});

    if (fcmRes.ok) {
      return json({ ok: true, message_id: fcmResult.name });
    } else {
      return json({ ok: false, error: fcmResult.error?.message ?? "FCM send failed" }, 500);
    }
  } catch (e) {
    console.error("send-push error:", e.message);
    return json({ error: "Push notification failed" }, 500);
  }
});
