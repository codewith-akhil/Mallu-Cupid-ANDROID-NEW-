// =============================================================================
// Mallu Cupid — verify-otp Edge Function (password-based auth)
// =============================================================================
// Verifies the 6-digit OTP code via the atomic consume_otp() function.
// On success: looks up the auth user by email + confirms their email.
//
// This does NOT create a user (the client already called signUp) and does NOT
// generate a session (the client calls signInWithPassword after this).
//
// POST /functions/v1/verify-otp
// Body: { "email": "user@example.com", "code": "123456" }
// Response: { "ok": true } or { "error": "..." }
// =============================================================================

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

async function consumeOtp(email: string, code: string): Promise<boolean> {
  const res = await fetch(`${SUPABASE_URL}/rest/v1/rpc/consume_otp`, {
    method: "POST",
    headers: {
      apikey: SERVICE_ROLE,
      Authorization: `Bearer ${SERVICE_ROLE}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ p_email: email, p_code: code }),
  });
  if (!res.ok) return false;
  const data = await res.json();
  // PostgREST returns a row of all-nulls when the function returns NULL
  if (data === null || data?.id === null || data?.id === undefined) return false;
  return true;
}

async function confirmEmail(email: string): Promise<boolean> {
  // Look up user by email via admin API
  const listRes = await fetch(
    `${SUPABASE_URL}/auth/v1/admin/users?per_page=1000`,
    { headers: { apikey: SERVICE_ROLE, Authorization: `Bearer ${SERVICE_ROLE}` } }
  );
  if (!listRes.ok) return false;
  const listData = await listRes.json();
  const user = listData?.users?.find(
    (u: { email?: string }) => (u.email ?? "").toLowerCase() === email
  );
  if (!user) return false;

  // Confirm email via admin API
  const confirmRes = await fetch(
    `${SUPABASE_URL}/auth/v1/admin/users/${user.id}`,
    {
      method: "PUT",
      headers: {
        apikey: SERVICE_ROLE,
        Authorization: `Bearer ${SERVICE_ROLE}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        email_confirm: true,
        app_metadata: { email_verified: true },
      }),
    }
  );
  return confirmRes.ok;
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
    return json({ error: "Invalid request" }, 400);
  }
  if (!email || !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) {
    return json({ error: "A valid email is required" }, 400);
  }
  if (!code || !/^\d{6}$/.test(String(code))) {
    return json({ error: "Code must be 6 digits" }, 400);
  }
  email = email.trim().toLowerCase();
  code = String(code).trim();

  try {
    // 1. Atomically consume the OTP
    const consumed = await consumeOtp(email, code);
    if (!consumed) {
      return json({ error: "Invalid or expired code" }, 401);
    }

    // 2. Confirm the user's email in Supabase auth
    const confirmed = await confirmEmail(email);
    if (!confirmed) {
      // OTP was consumed but we couldn't confirm email — still return ok
      // (the user might have been created by a different flow)
      console.warn("Could not confirm email for:", email);
    }

    return json({ ok: true });
  } catch (e) {
    console.error("verify-otp error:", e.message);
    return json({ error: "Verification failed" }, 500);
  }
});
