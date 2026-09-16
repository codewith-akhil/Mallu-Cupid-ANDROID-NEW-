// =============================================================================
// Mallu Cupid — reset-password Edge Function
// =============================================================================
// Updates a user's password after OTP verification.
// Uses the admin API (service role key) to set the new password.
//
// POST /functions/v1/reset-password
// Body: { "email": "user@example.com", "new_password": "NewPass123" }
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

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);

  let email: string | undefined;
  let newPassword: string | undefined;
  try {
    const body = await req.json();
    email = body?.email;
    newPassword = body?.new_password;
  } catch {
    return json({ error: "Invalid request" }, 400);
  }
  if (!email || !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) {
    return json({ error: "A valid email is required" }, 400);
  }
  if (!newPassword || newPassword.length < 8 || newPassword.length > 128) {
    return json({ error: "Password must be 8-128 characters" }, 400);
  }
  if (!/[A-Z]/.test(newPassword) || !/[0-9]/.test(newPassword)) {
    return json({ error: "Password must include an uppercase letter and a number" }, 400);
  }
  email = email.trim().toLowerCase();

  try {
    // 1. Look up user by email
    const listRes = await fetch(
      `${SUPABASE_URL}/auth/v1/admin/users?per_page=1000`,
      { headers: { apikey: SERVICE_ROLE, Authorization: `Bearer ${SERVICE_ROLE}` } }
    );
    if (!listRes.ok) {
      return json({ error: "Could not find account" }, 404);
    }
    const listData = await listRes.json();
    const user = listData?.users?.find(
      (u: { email?: string }) => (u.email ?? "").toLowerCase() === email
    );
    if (!user) {
      return json({ error: "No account found with this email" }, 404);
    }

    // 2. Update password via admin API
    const updateRes = await fetch(
      `${SUPABASE_URL}/auth/v1/admin/users/${user.id}`,
      {
        method: "PUT",
        headers: {
          apikey: SERVICE_ROLE,
          Authorization: `Bearer ${SERVICE_ROLE}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ password: newPassword }),
      }
    );

    if (!updateRes.ok) {
      return json({ error: "Could not update password" }, 500);
    }

    return json({ ok: true });
  } catch (e) {
    console.error("reset-password error:", e.message);
    return json({ error: "Could not update password" }, 500);
  }
});
