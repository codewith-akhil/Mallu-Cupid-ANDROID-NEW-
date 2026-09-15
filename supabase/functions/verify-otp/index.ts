// =============================================================================
// Mallu Cupid — verify-otp Edge Function (hardened)
// =============================================================================
// Verifies the 6-digit OTP via the atomic consume_otp() PL/pgSQL function
// (fixes TOCTOU race on attempts + used flag). On success:
//   1. Creates a Supabase auth user if one does not yet exist (via admin API)
//   2. Generates a magic-link token (NO email sent — we already verified via code)
//   3. Returns the token_hash for the client to exchange for a session
//
// POST /functions/v1/verify-otp
// Body: { "email": "user@example.com", "code": "123456" }
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

// Call the atomic consume_otp() RPC — returns the consumed row or null
async function consumeOtp(email: string, code: string): Promise<any | null> {
  const res = await fetch(`${SUPABASE_URL}/rest/v1/rpc/consume_otp`, {
    method: "POST",
    headers: {
      apikey: SERVICE_ROLE,
      Authorization: `Bearer ${SERVICE_ROLE}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ p_email: email, p_code: code }),
  });
  if (!res.ok) return null;
  const data = await res.json();
  // PostgREST returns a row of all-nulls when the function returns NULL
  // (e.g. {"id":null,"email":null,...}). Check if the result is actually
  // a consumed row by verifying the `id` field is non-null.
  if (data === null || data?.id === null || data?.id === undefined) {
    return null;
  }
  return data;
}

async function adminPost(path: string, body: object): Promise<any> {
  const res = await fetch(`${SUPABASE_URL}/auth/v1${path}`, {
    method: "POST",
    headers: {
      apikey: SERVICE_ROLE,
      Authorization: `Bearer ${SERVICE_ROLE}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(body),
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    // Handle "user already exists" gracefully — generate_link works for existing users
    if (data?.error_code === "user_exists" || data?.msg?.includes("already")) {
      throw new Error("USER_EXISTS");
    }
    throw new Error(data?.msg || data?.message || `adminPost ${path}: ${res.status}`);
  }
  return data;
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

  try {
    // 1. Atomically consume the OTP (fixes TOCTOU race on attempts + used)
    const consumed = await consumeOtp(email, code);
    if (!consumed) {
      // Could be: no active code, expired, too many attempts, or wrong code
      return json({ error: "Invalid or expired code. Request a new one." }, 401);
    }

    // 2. Ensure a Supabase auth user exists with a deterministic password.
    //    For NEW users: create with password, then authenticate via password grant.
    //    For EXISTING users: use generate_link to get a magic link token.
    const password = `MC!${Date.now()}#${Math.random().toString(36).slice(2, 10)}`;
    let userId: string;
    let accessToken: string | undefined;
    let refreshToken: string | undefined;
    let expiresIn: number | undefined;
    let tokenHash: string | undefined;
    let isNewUser = false;

    try {
      const created = await adminPost("/admin/users", {
        email,
        password,
        email_confirm: true,
        user_metadata: { name: "" },
      });
      userId = created?.id;
      isNewUser = true;
    } catch (e) {
      if (e.message === "USER_EXISTS" || e.message.includes("already")) {
        // Existing user — use generate_link (returns magic link token)
        const link = await adminPost("/admin/generate_link", { type: "magiclink", email });
        userId = link?.id;
        tokenHash = link?.hashed_token;
      } else {
        throw e;
      }
    }

    if (!userId) throw new Error("No user id in response");

    // 3. For new users: authenticate via password grant to get a session directly.
    //    For existing users: return the token_hash (client exchanges it).
    if (isNewUser) {
      const anonKey = Deno.env.get("ANON_KEY") ?? Deno.env.get("SUPABASE_ANON_KEY") ?? "";
      const tokenRes = await fetch(`${SUPABASE_URL}/auth/v1/token?grant_type=password`, {
        method: "POST",
        headers: { apikey: anonKey, "Content-Type": "application/json" },
        body: JSON.stringify({ email, password }),
      });
      if (tokenRes.ok) {
        const session = await tokenRes.json();
        accessToken = session.access_token;
        refreshToken = session.refresh_token;
        expiresIn = session.expires_in;
      }
    }

    return json({
      ok: true,
      user_id: userId,
      access_token: accessToken,
      refresh_token: refreshToken,
      expires_in: expiresIn,
      token_hash: tokenHash,
    });
  } catch (e) {
    console.error("verify-otp error:", e.message);
    // Never leak internal error details to the client
    return json({ error: "Verification failed. Try again." }, 500);
  }
});
