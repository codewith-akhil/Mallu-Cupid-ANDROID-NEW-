// Mallu Cupid — delete-account Edge Function
// Deletes a user's auth account (cascades to all tables via FK ON DELETE CASCADE).
// POST /functions/v1/delete-account  Body: { "user_id": "uuid" }
const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_ROLE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};
function json(body: Record<string, unknown>, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json", ...CORS } });
}
Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);
  let userId: string | undefined;
  try { const b = await req.json(); userId = b?.user_id; } catch { return json({ error: "Invalid request" }, 400); }
  if (!userId || !/^[0-9a-f-]{36}$/.test(userId)) return json({ error: "Valid user_id is required" }, 400);
  try {
    const res = await fetch(`${SUPABASE_URL}/auth/v1/admin/users/${userId}`, {
      method: "DELETE",
      headers: { apikey: SERVICE_ROLE, Authorization: `Bearer ${SERVICE_ROLE}` },
    });
    if (!res.ok && res.status !== 404) return json({ error: "Could not delete account" }, 500);
    return json({ ok: true });
  } catch (e) {
    return json({ error: "Could not delete account" }, 500);
  }
});
