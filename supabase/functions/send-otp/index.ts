// =============================================================================
// Mallu Cupid — send-otp Edge Function
// =============================================================================
// Generates a 6-digit OTP, stores it in public.otp_codes with a 10-minute
// expiry, and emails it via Resend. NEVER uses Supabase magic links.
//
// POST /functions/v1/send-otp
// Body: { "email": "user@example.com" }
// Response: { "ok": true, "expires_in": 600 }
// =============================================================================

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_ROLE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const RESEND_API_KEY = Deno.env.get("RESEND_API_KEY")!;
const RESEND_FROM = Deno.env.get("RESEND_FROM") ?? "Mallu Cupid <no-reply@mallucupid.app>";

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

function gen6(): string {
  // Cryptographically-random 6-digit code
  const arr = new Uint32Array(1);
  crypto.getRandomValues(arr);
  return String(100000 + (arr[0] % 900000));
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);

  let email: string | undefined;
  try {
    const body = await req.json();
    email = body?.email;
  } catch {
    return json({ error: "Invalid JSON body" }, 400);
  }
  if (!email || !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) {
    return json({ error: "A valid email is required" }, 400);
  }
  email = email.trim().toLowerCase();

  const supabase = createClient(SUPABASE_URL, SERVICE_ROLE, {
    auth: { persistSession: false },
  });

  // Rate-limit: max 3 unused codes per email in the last 10 minutes
  const tenMinAgo = new Date(Date.now() - 10 * 60 * 1000).toISOString();
  const { count } = await supabase
    .from("otp_codes")
    .select("id", { count: "exact", head: true })
    .eq("email", email)
    .eq("used", false)
    .gte("created_at", tenMinAgo);
  if (count && count >= 3) {
    return json({ error: "Too many requests. Try again later." }, 429);
  }

  const code = gen6();
  const expiresAt = new Date(Date.now() + 10 * 60 * 1000).toISOString();

  const { error: insErr } = await supabase.from("otp_codes").insert({
    email,
    code,
    expires_at: expiresAt,
    used: false,
    attempts: 0,
  });
  if (insErr) {
    console.error("otp insert failed:", insErr);
    return json({ error: "Could not generate code" }, 500);
  }

  // Send the email via Resend
  const emailRes = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${RESEND_API_KEY}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      from: RESEND_FROM,
      to: [email],
      subject: "Your Mallu Cupid verification code",
      html: `\
<div style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;max-width:480px;margin:0 auto;background:#201B18;color:#FFFAF5;padding:32px;border-radius:16px;">
  <div style="text-align:center;margin-bottom:24px;">
    <span style="font-size:28px;font-weight:700;color:#B24B39;">Mallu Cupid</span>
  </div>
  <h1 style="font-size:22px;margin:0 0 12px;font-weight:600;">Verify your email</h1>
  <p style="margin:0 0 24px;color:#EADBD2;line-height:1.5;">Use the code below to sign in to your Mallu Cupid account. It expires in 10 minutes.</p>
  <div style="text-align:center;background:#342823;border:1px solid rgba(255,250,245,0.12);border-radius:12px;padding:20px;margin:0 0 24px;">
    <span style="font-size:36px;font-weight:700;letter-spacing:8px;color:#E1AA98;">${code}</span>
  </div>
  <p style="margin:0;font-size:13px;color:#8F8179;line-height:1.5;">If you didn't request this code, you can safely ignore this email. Never share this code with anyone.</p>
</div>`,
    }),
  });

  if (!emailRes.ok) {
    const errText = await emailRes.text();
    console.error("Resend failed:", emailRes.status, errText);
    return json({ error: "Could not send verification email" }, 500);
  }

  return json({ ok: true, expires_in: 600 });
});
