# Subscription System — Database Design

## 1. Overview

Users own payment methods and subscriptions. Subscriptions are the hub of the
schema: they reference a plan (via a specific price version), generate
invoices over time, optionally track usage for metered billing, and can have
coupons applied. Invoices are settled by payments, which reference a payment
method and a real payment gateway transaction. Plans and coupons are never
hard-deleted — they're deprecated/archived so historical billing records stay
intact.

```
users ──┬─< payment_methods
        └─< subscriptions >─ plans ─< plan_versions
                 │      │  \
                 │      │   >─< coupons  (via subscription_coupons)
                 │      >─< plan_features (via subscription_features overrides)
                 ├─< invoices ─< invoice_line_items
                 ├─< invoices ─< payments >─ payment_methods
                 └─< usage_records
```

---

## 2. Core tables

### users
| column | type | notes |
|---|---|---|
| id | PK | |
| email | string | unique, indexed |
| created_at | timestamp | |

### payment_methods
| column | type | notes |
|---|---|---|
| id | PK | |
| user_id | FK -> users | |
| type | enum | card, bank, etc. |
| is_default | bool | |

### plans
| column | type | notes |
|---|---|---|
| id | PK | |
| name | string | |
| status | enum | `active`, `deprecated`, `archived` — see §5 |
| archived_at | timestamp, nullable | |

Note: `plans` no longer holds price directly — see `plan_versions` below.
Mutating a price in place breaks grandfathering; versioning it doesn't.

### plan_versions
| column | type | notes |
|---|---|---|
| id | PK | |
| plan_id | FK -> plans | |
| price | numeric | |
| currency | char(3) | default `USD` |
| effective_from | timestamp | |
| created_at | timestamp | |

A plan can have many versions over time. A subscription locks onto one
version at signup (or at its next renewal, if migrated) so a price change
never silently moves existing subscribers — "Pro" can be $99 for one cohort
and $129 for a newer one, both correct simultaneously.

### plan_features
| column | type | notes |
|---|---|---|
| id | PK | |
| plan_id | FK -> plans | |
| feature_key | string | e.g. `seats`, `api_calls`, `storage_gb` |
| value | string | |
| is_hard_limit | bool | block vs. warn on overage |

### subscription_features
| column | type | notes |
|---|---|---|
| id | PK | |
| subscription_id | FK -> subscriptions | |
| feature_key | string | |
| value | string | overrides the plan default for this subscription |
| overridden_at | timestamp | |

When support asks "why can this customer only add 5 seats" — this is what
you query, not the plan name.

### subscriptions
| column | type | notes |
|---|---|---|
| id | PK | |
| user_id | FK -> users | |
| plan_id | FK -> plans | |
| plan_version_id | FK -> plan_versions | the price this subscription actually bills at |
| status | enum | `trialing`, `active`, `past_due`, `canceled` — see §6 |
| current_period_start | timestamp | |
| current_period_end | timestamp | |
| cancel_at_period_end | bool | |
| retry_count | int | dunning retries, only meaningful in `past_due` |
| next_retry_at | timestamp, nullable | |
| pending_plan_id | FK -> plans, nullable | scheduled downgrade — see §7 |
| pending_change_at | timestamp, nullable | |

### invoices
| column | type | notes |
|---|---|---|
| id | PK | |
| subscription_id | FK -> subscriptions | |
| invoice_number | string | unique, sequential — `INV-2026-000123`. Required for tax/accounting compliance in most jurisdictions; don't rely on `id`. |
| idempotency_key | string, nullable | unique — see §8 |
| amount | numeric | computed/cached sum of line items, see §7 |
| status | enum | draft, open, paid, failed |
| issued_at | timestamp | |
| due_at | timestamp | |

### invoice_line_items
| column | type | notes |
|---|---|---|
| id | PK | |
| invoice_id | FK -> invoices | |
| type | enum | `subscription`, `proration_credit`, `proration_charge`, `one_off` |
| description | text | |
| amount | numeric | negative for credits |
| period_start | timestamp | |
| period_end | timestamp | |

### payments
| column | type | notes |
|---|---|---|
| id | PK | |
| invoice_id | FK -> invoices | |
| payment_method_id | FK -> payment_methods | |
| idempotency_key | string, nullable | unique — see §8 |
| gateway | string | e.g. `stripe`, `paddle`, `braintree` |
| gateway_transaction_id | string | reference for reconciling against gateway statements |
| gateway_response | json | raw webhook payload, kept for dispute/debugging |
| amount | numeric | |
| fee_amount | numeric | gateway processing fee |
| net_amount | numeric | amount − fee |
| status | enum | |
| paid_at | timestamp | |

### usage_records
| column | type | notes |
|---|---|---|
| id | PK | |
| subscription_id | FK -> subscriptions | |
| feature_key | string | |
| quantity | numeric | |
| recorded_at | timestamp | |

### coupons
| column | type | notes |
|---|---|---|
| id | PK | |
| code | string | unique, indexed |
| discount_type | enum | `percent`, `fixed_amount` |
| discount_value | numeric | |
| max_redemptions | int, nullable | null = unlimited |
| redemptions_count | int | denormalized counter, see §4 |
| max_redemptions_per_user | int, nullable | |
| starts_at | timestamp, nullable | |
| expires_at | timestamp, nullable | |
| status | enum | `active`, `disabled`, `expired` |

### subscription_coupons (junction table)
| column | type | notes |
|---|---|---|
| subscription_id | FK -> subscriptions | composite PK |
| coupon_id | FK -> coupons | composite PK |

### coupon_redemptions
| column | type | notes |
|---|---|---|
| id | PK | |
| coupon_id | FK -> coupons | |
| user_id | FK -> users | |
| subscription_id | FK -> subscriptions, nullable | |
| redeemed_at | timestamp | |

### subscription_events
| column | type | notes |
|---|---|---|
| id | PK | |
| subscription_id | FK -> subscriptions | |
| from_status | enum | |
| to_status | enum | |
| reason | text | |
| occurred_at | timestamp | |

---

## 3. Design principles

- **Never store live/derivable state as the only source of truth.** Price
  lives in `plan_versions`, not as a mutable column on `plans` — a
  subscription locks onto a version, and an invoice line item locks onto
  the price at billing time. Three layers, each answering a different
  question: "what could this plan cost," "what does this subscriber pay,"
  "what was actually charged."
- **Ledgers over counters.** `coupons.redemptions_count` is a fast-read cache;
  `coupon_redemptions` is the real ledger used for enforcement and audit.
- **Soft-delete anything with financial history attached.** Plans, coupons —
  archive/deprecate, never hard-delete once referenced.
- **Append-only where money is involved.** Invoices are composed of line
  items rather than a single mutable `amount`, so proration, refunds, and
  disputes don't require overwriting history.
- **Idempotency at every write boundary that a gateway can retry.** Webhook
  redelivery is routine, not exceptional — see §8.

---

## 4. Coupon redemption limits and expiry

Validation at redemption time, in order:
1. `status = 'active'`
2. `now() between starts_at and expires_at` (null = open-ended)
3. `redemptions_count < max_redemptions` (skip if null)
4. `count(*) from coupon_redemptions where coupon_id=X and user_id=Y < max_redemptions_per_user`

Then, in the same transaction as creating the subscription/invoice, increment
the counter and insert into `coupon_redemptions` — so a failed payment
doesn't burn a redemption.

**Race condition guard** — use an atomic conditional update instead of
read-then-write:
```sql
UPDATE coupons
SET redemptions_count = redemptions_count + 1
WHERE id = $1 AND redemptions_count < max_redemptions
RETURNING id;
```
No row returned → coupon is exhausted, reject the redemption.

---

## 5. Deleting a plan with active subscriptions

Don't allow it. Set the FK to `ON DELETE RESTRICT` by default so the database
refuses the delete if any subscription still references the plan, active or
not. Instead:

- `status = 'deprecated'` — no longer offered to new customers; existing
  subscriptions keep billing against their locked `plan_version_id`.
- `status = 'archived'` — fully retired, relevant only for historical
  reporting.
- Forcing migration off a deprecated plan is an explicit application
  workflow (notify → offer new plan → migrate on next renewal), not a
  cascading delete.

If a plan truly has zero subscriptions ever attached, allow a real delete
guarded at the DB level:
```sql
DELETE FROM plans
WHERE id = $1
AND NOT EXISTS (
  SELECT 1 FROM subscriptions WHERE plan_id = $1
);
```

---

## 6. Subscription status state machine

States: `trialing`, `active`, `past_due`, `canceled` (terminal).

```
trialing --trial converts--> active
trialing --trial expires, no payment--> canceled
active --payment fails--> past_due
past_due --recovered--> active
past_due --dunning exhausted--> canceled
active --user cancels (immediate or cancel_at_period_end)--> canceled
```

- Wrap every transition in a service layer that validates the current state
  allows the target state — `canceled` never transitions back to `active`;
  a reactivation creates a *new* subscription.
- Log every transition to `subscription_events` — this is what support and
  analytics actually query ("why did this account churn"), not the current
  status column alone.
- `past_due` carries `retry_count` and `next_retry_at` so the dunning job
  doesn't have to re-derive what to do next. A fixed retry schedule (e.g.
  `[1, 3, 7, 14]` days) in application config is enough until you have a
  concrete reason — a distinct tier with different retry treatment — to
  promote it into its own table.
- Distinguish `cancel_at_period_end = true` (still active, lapses at next
  renewal) from an immediate cancel — most systems need both.

---

## 7. Proration and upgrades

When a subscription changes plan mid-cycle, generate new invoice line items
rather than mutating the existing invoice. Amounts are computed against the
subscription's `plan_version_id`, not a live `plans.price` lookup.

**Example** — plan A ($10/mo) → plan B ($30/mo), 10 days into a 30-day cycle
(20 days remaining):

1. Unused value on plan A: `(20/30) * $10 = $6.67` → `proration_credit` line
   item for `-$6.67`.
2. Charge for remaining time on plan B: `(20/30) * $30 = $20.00` →
   `proration_charge` line item for `+$20.00`.
3. Net the two into an invoice — charged immediately or rolled into the next
   scheduled bill, a product decision.
4. Update `subscriptions.plan_id` and `plan_version_id` to B's current
   version. Whether `current_period_start`/`end` reset depends on whether
   the upgrade resets the billing cycle or keeps the original renewal date.

**Downgrades** use the same math but are usually deferred to the next
renewal so the user doesn't lose access mid-cycle they already paid for —
stored via `subscriptions.pending_plan_id` / `pending_change_at` and applied
by a background job, not applied as immediate proration.

`invoice_line_items` is what makes this work cleanly: the invoice becomes a
container for priced line items instead of one fixed number, so every
proration, refund, or dispute stays auditable rather than requiring history
to be overwritten.

---

## 8. Idempotency and gateway reconciliation

Webhook redelivery from a payment gateway is routine, not an edge case —
design for it from day one rather than discovering double-charges in
production.

- `invoices.idempotency_key` and `payments.idempotency_key` are both unique.
  Before creating either row, check for an existing key and short-circuit if
  found, rather than relying on the caller to never retry.
- `payments.gateway_transaction_id` is the join key back to the gateway's
  own dashboard/statements — required for any "does our data match Stripe's"
  reconciliation.
- `payments.gateway_response` stores the raw webhook payload. When a
  dispute or a "customer says they paid" ticket comes in six months later,
  this is what you actually look at — a parsed `status` column alone won't
  have the detail you need.
- `fee_amount` / `net_amount` on payments answer "what's our revenue after
  processing fees" directly, instead of requiring a manual reconciliation
  against the gateway's own fee reports.

**Scope note on tax:** computing tax correctly (rate-by-jurisdiction,
tax-on-tax in compounding regions, exemptions) is its own hard problem.
Rather than modeling tax rates in this schema, treat it as a service
boundary — integrate a dedicated tax provider (Stripe Tax, Avalara, etc.)
and store its output (`tax_amount`, `tax_jurisdiction`) as an
`invoice_line_items` row of type `one_off` or a dedicated `tax` type, rather
than trying to own tax-rate logic here.
