// =============================================================================
// Mallu Cupid — send-otp Edge Function (hardened)
// =============================================================================
// Generates a 6-digit OTP, stores it with a SHA-256 hash in otp_codes,
// enforces per-email AND per-IP rate limits, emails it via Resend.
// Dev fallback (dev_code) is gated behind ALLOW_DEV_CODE env flag.
//
// POST /functions/v1/send-otp
// Body: { "email": "user@example.com" }
// =============================================================================

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_ROLE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const RESEND_API_KEY = Deno.env.get("RESEND_API_KEY");
const RESEND_FROM = Deno.env.get("RESEND_FROM") ?? "Mallu Cupid <no-reply@mallucupid.app>";
const ALLOW_DEV_CODE = Deno.env.get("ALLOW_DEV_CODE") === "true";

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
  const arr = new Uint32Array(1);
  crypto.getRandomValues(arr);
  return String(100000 + (arr[0] % 900000));
}

async function sha256hex(text: string): Promise<string> {
  // HMAC-SHA256 with a fixed secret key — must match the consume_otp() PL/pgSQL function
  // which uses: hmac(p_code::bytea, 'mallu_cupid_otp_secret'::bytea, 'sha256')
  const encoder = new TextEncoder();
  const key = await crypto.subtle.importKey(
    "raw",
    encoder.encode("mallu_cupid_otp_secret"),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const sig = await crypto.subtle.sign("HMAC", key, encoder.encode(text));
  return Array.from(new Uint8Array(sig)).map(b => b.toString(16).padStart(2, "0")).join("");
}

async function postgrest(path: string, body: object): Promise<Response> {
  return fetch(`${SUPABASE_URL}/rest/v1${path}`, {
    method: "POST",
    headers: {
      apikey: SERVICE_ROLE,
      Authorization: `Bearer ${SERVICE_ROLE}`,
      "Content-Type": "application/json",
      Prefer: "return=minimal",
    },
    body: JSON.stringify(body),
  });
}

async function postgrestCount(path: string): Promise<number> {
  const res = await fetch(`${SUPABASE_URL}/rest/v1${path}`, {
    method: "GET",
    headers: {
      apikey: SERVICE_ROLE,
      Authorization: `Bearer ${SERVICE_ROLE}`,
      Prefer: "count=exact",
      Range: "0-0",
    },
  });
  const range = res.headers.get("content-range");
  if (range) {
    const m = range.match(/\/(\d+)/);
    if (m) return parseInt(m[1], 10);
  }
  return 0;
}

async function getClientIP(req: Request): Promise<string> {
  // Supabase edge functions forward client IP via these headers
  const cf = req.headers.get("cf-connecting-ip");
  if (cf) return cf;
  const xff = req.headers.get("x-forwarded-for");
  if (xff) return xff.split(",")[0].trim();
  return "unknown";
}

async function checkIPRateLimit(ip: string): Promise<boolean> {
  // Allow max 5 OTP requests per IP per 10 minutes
  const tenMinAgo = new Date(Date.now() - 10 * 60 * 1000).toISOString();
  const count = await postgrestCount(
    `/otp_ip_throttle?ip=eq.${encodeURIComponent(ip)}&window_start=gte.${tenMinAgo}`
  );
  if (count >= 5) return false;  // blocked

  // Record this request
  await postgrest("/otp_ip_throttle", { ip, window_start: new Date().toISOString(), hits: 1 });
  return true;  // allowed
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

  // 1. Per-IP rate limit
  const clientIP = await getClientIP(req);
  const ipAllowed = await checkIPRateLimit(clientIP);
  if (!ipAllowed) {
    return json({ error: "Too many requests from your IP. Try again later." }, 429);
  }

  // 2. Per-email rate limit (max 3 unused codes in 10 min)
  const tenMinAgo = new Date(Date.now() - 10 * 60 * 1000).toISOString();
  const emailCount = await postgrestCount(
    `/otp_codes?email=eq.${encodeURIComponent(email)}&used=eq.false&created_at=gte.${tenMinAgo}`
  );
  if (emailCount >= 3) {
    return json({ error: "Too many requests. Try again later." }, 429);
  }

  // 3. Generate code + hash
  const code = gen6();
  const codeHash = await sha256hex(code);
  const expiresAt = new Date(Date.now() + 10 * 60 * 1000).toISOString();

  // 4. Store with hash (code column kept null for new rows — hash is source of truth)
  const insRes = await postgrest("/otp_codes", {
    email,
    code: null,
    code_hash: codeHash,
    expires_at: expiresAt,
    used: false,
    attempts: 0,
  });
  if (!insRes.ok) {
    console.error("otp insert failed:", insRes.status, await insRes.text());
    return json({ error: "Could not generate code" }, 500);
  }

  // 5. Send email via Resend (only if API key is configured)
  if (!RESEND_API_KEY) {
    if (ALLOW_DEV_CODE) {
      return json({ ok: true, expires_in: 600, dev_code: code, dev_note: "Resend not configured — dev mode" });
    }
    return json({ error: "Email delivery is not configured" }, 502);
  }

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
      html: `<div style="font-family:sans-serif;max-width:480px;margin:0 auto;background:#201B18;color:#FFFAF5;padding:32px;border-radius:16px;">
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
    if (ALLOW_DEV_CODE) {
      return json({ ok: true, expires_in: 600, dev_code: code, dev_note: "Resend failed — dev mode" });
    }
    return json({ error: "Could not send verification email" }, 502);
  }

  return json({ ok: true, expires_in: 600 });
});
