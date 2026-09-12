// =============================================================================
// Mallu Cupid — verify-otp Edge Function (no external deps, pure Deno fetch)
// =============================================================================
// Verifies the 6-digit OTP. On success:
//   1. Marks the OTP row used (PostgREST PATCH).
//   2. Looks up / creates a Supabase auth user (Admin API).
//   3. Generates a magic-link token (Admin API — NO email sent).
//   4. Returns the token_hash. The client then calls
//      supabase.auth.verifyOtp({ token_hash, type: 'magiclink' }) to obtain a
//      real session.
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

async function pgGet(path: string): Promise<any | null> {
  const res = await fetch(`${SUPABASE_URL}/rest/v1${path}`, {
    headers: {
      apikey: SERVICE_ROLE,
      Authorization: `Bearer ${SERVICE_ROLE}`,
      Accept: "application/vnd.pgrst.object+json",
    },
  });
  if (res.status === 406) return null; // no rows
  if (!res.ok) throw new Error(`pgGet ${path}: ${res.status}`);
  return res.json();
}

async function pgPatch(path: string, body: object): Promise<void> {
  const res = await fetch(`${SUPABASE_URL}/rest/v1${path}`, {
    method: "PATCH",
    headers: {
      apikey: SERVICE_ROLE,
      Authorization: `Bearer ${SERVICE_ROLE}`,
      "Content-Type": "application/json",
      Prefer: "return=minimal",
    },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`pgPatch ${path}: ${res.status}`);
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
  if (!res.ok) throw new Error(data?.msg || data?.message || `adminPost ${path}: ${res.status}`);
  return data;
}

async function adminGet(path: string): Promise<any> {
  const res = await fetch(`${SUPABASE_URL}/auth/v1${path}`, {
    headers: {
      apikey: SERVICE_ROLE,
      Authorization: `Bearer ${SERVICE_ROLE}`,
    },
  });
  if (!res.ok) throw new Error(`adminGet ${path}: ${res.status}`);
  return res.json();
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
    // 1. Look up the most recent unused, non-expired code for this email
    const otpRow = await pgGet(
      `/otp_codes?email=eq.${encodeURIComponent(email)}&used=eq.false&order=created_at.desc&limit=1`
    );
    if (!otpRow) {
      return json({ error: "No active code. Request a new one." }, 404);
    }

    // 2. Rate-limit attempts
    if (otpRow.attempts >= 5) {
      return json({ error: "Too many attempts. Request a new code." }, 429);
    }
    await pgPatch(`/otp_codes?id=eq.${otpRow.id}`, { attempts: otpRow.attempts + 1 });

    // 3. Expiry check
    if (new Date(otpRow.expires_at).getTime() < Date.now()) {
      return json({ error: "Code expired. Request a new one." }, 410);
    }

    // 4. Code match
    if (otpRow.code !== code) {
      return json({ error: "Invalid code" }, 401);
    }

    // 5. Mark used
    await pgPatch(`/otp_codes?id=eq.${otpRow.id}`, { used: true });

    // 6. Look up existing auth user by email
    const userList = await adminGet(`/admin/users?page=1&per_page=1000`);
    let userId: string | undefined;
    if (userList?.users) {
      const found = userList.users.find(
        (u: { email?: string }) => (u.email ?? "").toLowerCase() === email
      );
      if (found) userId = found.id;
    }

    // 7. If no user, create one
    if (!userId) {
      const created = await adminPost("/admin/users", {
        email,
        password: `Mallu${Date.now()}!${Math.random().toString(36).slice(2, 8)}`,
        email_confirm: true,
        user_metadata: { name: "" },
      });
      userId = created?.id;
      if (!userId) throw new Error("Could not create user");
    }

    // 8. Generate a magic-link token (NO email is sent — we already verified via code)
    const link = await adminPost("/admin/generate_link", {
      type: "magiclink",
      email,
    });
    const tokenHash = link?.hashed_token;
    if (!tokenHash) throw new Error("No hashed_token in generate_link response");

    return json({ ok: true, user_id: userId, token_hash: tokenHash });
  } catch (e) {
    console.error("verify-otp error:", e.message);
    return json({ error: "Verification failed. Try again.", dev_error: e.message }, 500);
  }
});
