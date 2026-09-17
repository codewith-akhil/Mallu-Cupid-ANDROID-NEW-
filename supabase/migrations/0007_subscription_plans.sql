-- Update subscriptions table for MalluCupid Pro plans
begin;

-- Add duration_days column + plan_name column
alter table public.subscriptions
  add column if not exists plan_name text,
  add column if not exists duration_days int,
  add column if not exists google_purchase_token text,
  add column if not exists google_product_id text;

-- Update the CHECK constraint for plan names
alter table public.subscriptions
  drop constraint if exists subscriptions_plan_check;
alter table public.subscriptions
  add constraint subscriptions_plan_check
  check (plan in ('weekly_pro', 'monthly_pro', 'yearly_pro'));

commit;
