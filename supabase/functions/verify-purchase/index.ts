// Mallu Cupid — verify-purchase Edge Function
// Verifies a Google Play Billing purchase token + saves subscription to DB.
// POST /functions/v1/verify-purchase
// Body: { "user_id": "uuid", "product_id": "weekly_pro|monthly_pro|yearly_pro",
//         "purchase_token": "xxx", "order_id": "GPA.xxx" }
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
const PLANS: Record<string, { price: number; days: number }> = {
  weekly_pro: { price: 49, days: 7 },
  monthly_pro: { price: 99, days: 30 },
  yearly_pro: { price: 799, days: 365 },
};
Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);
  let body: any;
  try { body = await req.json(); } catch { return json({ error: "Invalid request" }, 400); }
  const { user_id, product_id, purchase_token, order_id } = body;
  if (!user_id || !product_id || !purchase_token) return json({ error: "Missing fields" }, 400);
  const plan = PLANS[product_id];
  if (!plan) return json({ error: "Invalid product_id" }, 400);
  try {
    // NOTE: Full Google Play Developer API verification requires OAuth2 service account.
    // For now we trust the purchase_token (client-side verification).
    // TODO: Add server-side verification via Google Play Developer API.
    const now = new Date();
    const expires = new Date(now.getTime() + plan.days * 24 * 60 * 60 * 1000);
    // Insert subscription
    const res = await fetch(`${SUPABASE_URL}/rest/v1/subscriptions`, {
      method: "POST",
      headers: { apikey: SERVICE_ROLE, Authorization: `Bearer ${SERVICE_ROLE}`, "Content-Type": "application/json", Prefer: "return=representation" },
      body: JSON.stringify({ user_id, plan: product_id, plan_name: "MalluCupid Pro", status: "active", amount: plan.price, txn_id: order_id || purchase_token, google_purchase_token: purchase_token, google_product_id: product_id, duration_days: plan.days, started_at: now.toISOString(), expires_at: expires.toISOString() }),
    });
    if (!res.ok) { const t = await res.text(); console.error("DB insert failed:", res.status, t); return json({ error: "Could not save subscription" }, 500); }
    const data = await res.json();
    return json({ ok: true, subscription: data?.[0] ?? null });
  } catch (e) { console.error("verify-purchase error:", e.message); return json({ error: "Verification failed" }, 500); }
});
