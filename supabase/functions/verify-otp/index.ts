// =============================================================================
// Mallu Cupid — verify-otp Edge Function
// =============================================================================
// Verifies the 6-digit OTP. On success:
//   1. Marks the OTP row used.
//   2. Creates a Supabase auth user if one does not yet exist for the email.
//   3. Generates a magic-link token via the Admin API (NO email is sent — we
//      already emailed the code via Resend).
//   4. Returns the token_hash + user_id. The client then calls
//      supabase.auth.verifyOtp({ token_hash, type: 'magiclink' }) to obtain a
//      real session.
//
// POST /functions/v1/verify-otp
// Body: { "email": "user@example.com", "code": "123456" }
// Response: { "ok": true, "user_id": "...", "token_hash": "..." }
// =============================================================================

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_ROLE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

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

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);

  let email: string | undefined;
  let code: string | undefined;
  try {
    const body = await req.json();
    email = body?.email;
    code = body?.code;
  } catch {
    return json({ error: "Invalid JSON body" }, 400);
  }
  if (!email || !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) {
    return json({ error: "A valid email is required" }, 400);
  }
  if (!code || !/^\d{6}$/.test(String(code))) {
    return json({ error: "Code must be 6 digits" }, 400);
  }
  email = email.trim().toLowerCase();
  code = String(code).trim();

  const supabase = createClient(SUPABASE_URL, SERVICE_ROLE, {
    auth: { persistSession: false },
  });

  // 1. Look up the most recent unused, non-expired code for this email
  const { data: otpRow, error: otpErr } = await supabase
    .from("otp_codes")
    .select("id, code, expires_at, attempts")
    .eq("email", email)
    .eq("used", false)
    .order("created_at", { ascending: false })
    .limit(1)
    .maybeSingle();

  if (otpErr) {
    console.error("otp lookup failed:", otpErr);
    return json({ error: "Verification failed" }, 500);
  }
  if (!otpRow) {
    return json({ error: "No active code. Request a new one." }, 404);
  }

  // 2. Rate-limit attempts
  if (otpRow.attempts >= 5) {
    return json({ error: "Too many attempts. Request a new code." }, 429);
  }
  await supabase
    .from("otp_codes")
    .update({ attempts: otpRow.attempts + 1 })
    .eq("id", otpRow.id);

  // 3. Expiry check
  if (new Date(otpRow.expires_at).getTime() < Date.now()) {
    return json({ error: "Code expired. Request a new one." }, 410);
  }

  // 4. Code match
  if (otpRow.code !== code) {
    return json({ error: "Invalid code" }, 401);
  }

  // 5. Mark used
  const { error: usedErr } = await supabase
    .from("otp_codes")
    .update({ used: true })
    .eq("id", otpRow.id);
  if (usedErr) console.warn("otp mark-used failed:", usedErr);

  // 6. Ensure a Supabase auth user exists for this email. createUser is
  //    idempotent-ish: if the user exists it returns the existing one when
  //    email_confirm is true, or throws otherwise — handle both.
  const { data: existing, error: listErr } = await supabase.auth.admin.listUsers();
  let userId: string | undefined;
  if (!listErr && existing?.users) {
    const found = existing.users.find(
      (u: { email?: string }) => (u.email ?? "").toLowerCase() === email,
    );
    if (found) userId = found.id;
  }
  if (!userId) {
    const { data: newUser, error: createErr } = await supabase.auth.admin.createUser({
      email,
      email_confirm: true,
    });
    if (createErr) {
      console.error("createUser failed:", createErr);
      return json({ error: "Could not create account" }, 500);
    }
    userId = newUser.user.id;
  }

  // 7. Generate a magic-link token (NO email is sent — we already verified).
  const { data: link, error: linkErr } = await supabase.auth.admin.generateLink({
    type: "magiclink",
    email,
  });
  if (linkErr || !link?.properties?.hashed_token) {
    console.error("generateLink failed:", linkErr);
    return json({ error: "Could not start session" }, 500);
  }

  return json({
    ok: true,
    user_id: userId,
    token_hash: link.properties.hashed_token,
  });
});
