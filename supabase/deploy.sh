#!/usr/bin/env bash
# =============================================================================
# Mallu Cupid — Supabase Edge Function deploy script
# =============================================================================
# Run this on a machine with Docker installed (for the Supabase CLI bundler).
#
#   1. Install the Supabase CLI:  https://supabase.com/docs/guides/cli
#   2. Set your access token:     export SUPABASE_ACCESS_TOKEN=sbp_...
#   3. Set the Resend API key:    export RESEND_API_KEY=re_...
#   4. Run this script:           ./supabase/deploy.sh
#
# =============================================================================
set -euo pipefail

PROJECT_REF="zetvumxhtkomajmdxfbx"

: "${SUPABASE_ACCESS_TOKEN:?SUPABASE_ACCESS_TOKEN is required (sbp_...)}"
: "${RESEND_API_KEY:?RESEND_API_KEY is required (re_...)}"

echo "→ Logging in to Supabase…"
supabase login --token "$SUPABASE_ACCESS_TOKEN"

echo "→ Linking project $PROJECT_REF…"
supabase link --project-ref "$PROJECT_REF" || true

echo "→ Setting Resend secrets…"
supabase secrets set \
  RESEND_API_KEY="$RESEND_API_KEY" \
  RESEND_FROM="Mallu Cupid <no-reply@mallucupid.app>"

echo "→ Deploying send-otp (public, no JWT verify)…"
supabase functions deploy send-otp --no-verify-jwt

echo "→ Deploying verify-otp (public, no JWT verify)…"
supabase functions deploy verify-otp --no-verify-jwt

echo "✓ Done. Endpoints:"
echo "  POST https://$PROJECT_REF.supabase.co/functions/v1/send-otp"
echo "  POST https://$PROJECT_REF.supabase.co/functions/v1/verify-otp"
