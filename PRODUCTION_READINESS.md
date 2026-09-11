# Qliina Management — Production Readiness Checklist

Ordered by effort (lightest first). Work in this order, merge independently, tick
items off here as they land, and keep the file in the repo so it survives
interruptions. Re-verify with the commands at the bottom.

**Current task: payment-acceptance re-scope.** Businesses must always be able to
record a card/transfer payment manually (external POS, direct bank transfer) —
**manual record is the always-on default and is never blocked, even when gateway
providers are configured.** Provider connections are additive, optional business
config. (Archived previous checklist: `archive/PRODUCTION_READINESS.md`.)

## ✅ Phase 0 — Manual record restores the always-on core (de-regression)

- [x] **Optional `provider` on payment** — `PaymentService.processPayment`:
      `provider` is optional for CARD/TRANSFER. Omitted → record the payment as
      `COMPLETED` immediately with the staff-entered reference
      (last4/authorization/txn id), same accounting path as CASH (blank reference
      → generated `ql_<uuid>`). Supplied →
      existing charge path unchanged (redirect/PENDING/webhook settle). Keep the
      `PROVIDER_UNKNOWN` / `PROVIDER_DISABLED` / `PROVIDER_NOT_CONFIGURED` 400s
      **only** when a provider is actually supplied — never silently downgrade a
      business that picked a gateway.
- [x] **Split legs** — `PaymentService.splitPayment` uses the same optional-
      provider rule per leg; only `COMPLETED` legs count toward paid totals
      (declined/pending legs no longer double-count).
- [x] **Invariant** — manual record always available for CARD/TRANSFER with zero
      provider config; provider presence only *adds* gateway options.
- [x] **Tests** — `PaymentProviderIntegrationTest.processPayment_cardWithoutProvider`
      (was 400 `PAYMENT_PROVIDER_REQUIRED`) → `COMPLETED` + order settled; no-provider
      CARD = fully-paid integration cases (with & without reference); existing
      disabled/unknown/unconfigured 400 tests kept; split cases with a no-provider
      leg + declined/pending legs not counted as paid. Full `./mvnw test` green (561).

## ✅ Phase 1 — Connection model (advanced per-business config)

- [x] **`PaymentProviderConfig` extension** — `{provider, enabled}` grows to
      `connectionMode` (`DISCONNECTED` | `PLATFORM` | `BYO`), `credentialsEncrypted`
      (AES-GCM via `EncryptionService`, decrypted only inside the payment service),
      and `platformSubaccountId`. Unique `(business_id, provider)` kept. Manual is
      orthogonal — never represented as a provider state; `DISCONNECTED` simply
      means "no gateway".
- [x] **SPI per-connection** — `PaymentProvider` gains
      `supportsPlatformSubaccounts()` and the charge path carries the per-business
      connection context (subaccount ref or BYO credentials). `available` for a
      business = `PLATFORM` with the platform flag on, or `BYO` with encrypted
      credentials present; both fail closed otherwise.
- [x] **Config surface** — `PaymentProviderDTO` gains `connectionMode` +
      `hasCredentials` (never the secret). `PUT /api/v1/{businessId}/payment-providers/{name}/connection`
      accepts `{mode, secretKey?}`; BYO secrets are encrypted at rest and never
      returned to the client.
- [x] **Simulator stands in** for both `PLATFORM` (simulated subaccount) and
      `BYO` in dev/test; real Paystack/Flutterwave platform keys deferred until
      provider-side platform/subaccount APIs are confirmed.
- [x] **Tests** — connection modes persist per business; BYO never surfaces in
      responses; misconfigured/unconfigured connections fail closed; list +
      connection-toggle stay un-clock-gated (settings surface).

> **Known follow-ups (Phase 3/ops):** `verify()`/`refund()`/webhook-signature
> still use the provider's **platform** secret — a BYO business reconciles via
> webhooks/PENDING settle until the per-business credential is plumbed through
> verify/refund too (payment rows carry no connection snapshot yet).
> `db/baseline.sql` needs regenerating after the next greenfield Postgres bootstrap
> (3 new nullable columns).

## ✅ Phase 2 — Generate payment, scan/QR, webhook auto-link

- [x] **Generate payment** — `POST /api/v1/{businessId}/payments/orders/{orderId}/generate`
      (`@RequireClockIn`, `payment.process`): `GeneratePaymentRequest {amount?, method?, provider}`
      with amount defaulting to the order balance due (total − `sumCompletedPayments`).
      Resolves the business's connection (fail-closed: unknown/disabled/unconfigured →
      `PROVIDER_UNKNOWN`/`PROVIDER_DISABLED`/`PROVIDER_NOT_CONFIGURED`), calls the SPI
      `initiateCheckout` (new default = `charge`; simulator overrides to always produce a
      redirect checkout URL), refuses over-balance → `PAYMENT_AMOUNT_EXCEEDS_DUE` and
      fully-paid/≤0 → `ORDER_ALREADY_PAID`, and persists a `PENDING` `OrderPayment`
      (reference `ql_<uuid>`, providerReference, providerStatus `PENDING`, metadata
      `checkoutUrl`, collectedBy = current user) + order-timeline entry. Returns
      `GeneratePaymentResultDTO {…, checkoutUrl, qrPayload (=checkoutUrl), balanceDue, isFullyPaid}`.
      PENDING is excluded from paid totals via `sumCompletedPaymentsByOrderId`. The
      existing `PaymentFilter` `orderId` + `status` supports the PENDING list (no new
      surface); recheck stays on the existing verify endpoint.
- [x] **Webhook auto-link hardening** — `PaymentProviderService.processWebhook` matches by
      provider + reference **and cross-checks amount** before settle: mismatch → payment
      stays `PENDING` with providerStatus `AMOUNT_MISMATCH` (never settled); `charge.failed`
      events (SPI `WebhookEvent` gained `chargeFailed`; simulator/paystack/flutterwave
      adapters updated) transition the payment to `FAILED`.
- [x] **Unmatched funds queue** — `PaymentReconciliationItem` (global OPEN queue,
      cross-tenant by design) upserted on (provider, providerReference) when a settled
      webhook has no matching payment. `GET /api/v1/{businessId}/payment-reconciliation`
      (`payment.view`, OPEN = all businesses' / RESOLVED = this business's) +
      `POST …/{itemId}/resolve` (`payment.process`, `{orderId, notes?}` → RESOLVED,
      validates order belongs to the business). New errorCodes:
      `RECONCILIATION_NOT_FOUND`, `RECONCILIATION_ALREADY_RESOLVED`, `PROVIDER_CHECKOUT_FAILED`.
- [x] **Pending/verify surface** — covered by existing endpoints; asserted in tests
      (`listPayments_pendingFilterCapturesGeneratedLink`).

> **Tests:** 12 new in `PaymentProviderIntegrationTest` (generate default/custom/fail-closed/
> unknown/already-paid/over-balance, PENDING list filter, amount-mismatch no-settle,
> charge.failed → FAILED, unmatched-fund item, resolve → RESOLVED + scoping, unknown item).
> Full `./mvnw test` green (580).

## ✅ Phase 3 — Ops hardening

- [x] **PENDING sweep** — scheduled expiry/cleanup of abandoned PENDING payments.
      `sweepExpiredPendingBefore(cutoff)` marks `status=PENDING` +
      `providerStatus=PENDING` (only) as `FAILED` / `providerStatus=EXPIRED` —
      staff-review states like `AMOUNT_MISMATCH` are preserved — appends an
      order timeline entry and a `PAYMENT_UPDATED` WS event. Runs nightly
      (`app.payments.pending-expiry-cron`, default `0 15 3 * * *`) with
      `app.payments.pending-expiry-hours` (default 24, env
      `PAYMENTS_PENDING_EXPIRY_HOURS`) cutoff.
- [x] **Charge idempotency** — `generatePaymentLink` re-presents a single active
      `PENDING` per order+provider (`findPendingByOrderAndProvider`, both status
      and providerStatus `PENDING`): re-opening Link/QR returns the same
      payment/checkoutUrl instead of stacking a duplicate charge; once settled or
      swept the authorization is gone and the normal guards apply.
- [x] **Provider refund parity** — `verify(...)`/`refund(...)` gained default
      `Connection`-aware overloads on the SPI; `PaymentProviderService` resolves
      the business's connection (subaccount / decrypted BYO credentials) and
      verifies/refunds with it. Paystack/Flutterwave override to authenticate a
      BYO business's recheck/refund with its own secret (falling back to the
      platform key); simulator inherits the defaults. `refundPayment` no longer
      fail-closes on the platform key when BYO credentials exist.

> Full `./mvnw test` green (585).

## ✅ Phase 4 — Platform payment-provider control (admin availability)

- [x] **Platform toggle** — `PlatformPaymentProviderConfig` (lazy rows; **no row =
      enabled**, preserving prior behavior) + `GET /api/v1/admin/payment-providers`
      and `PATCH .../{provider}/availability?platformEnabled=` (`platform.payments.manage`;
      catalog GET also `platform.businesses.view`). Materializes on first toggle,
      records `updated_by_username` + `updated_at` for the audit trail.
- [x] **Fail-closed everywhere** — a platform-disabled provider is hidden from
      business gateway lists, its business config rows are treated as `enabled=false`
      (checkout → `PROVIDER_DISABLED`), and business connect/enable is rejected with
      `PROVIDER_UNAVAILABLE`. In-flight authorizations keep verifying/refunding and
      reconciling through webhooks — a toggle never strands money already initiated.
- [x] **Permission wiring** — `platform.payments.manage` added to PermissionSeeder
      and granted to PLATFORM_ADMIN + SUPER_ADMIN. `RoleSeeder.ensurePlatformRolePermissions()`
      tops up SUPER_ADMIN with **all** current permissions each boot (the live admin's JWT
      was missing the new permission because SUPER_ADMIN's set is a role-creation snapshot).
- [x] **Toggle race (`PROVIDER_UPDATE_CONFLICT`)** — `setPlatformEnabled` runs each
      attempt in a fresh REQUIRES_NEW transaction: a concurrent-toggle collision
      (unique-constraint on a not-yet-created row, or `@Version` bump) rolls back the
      loser cleanly and is retried (3 attempts) against the winner's row; exhaustion
      returns 409 instead of 500. `saveAndFlush` surfaces the constraint inside the attempt.
- [x] **Tests** — `AdminPaymentProviderIntegrationTest` (7): catalog shape, admin authz
      (read OR `/scale`, patch requires manage), toggle round-trip + persistence across
      boots, unknown provider → `PROVIDER_UNKNOWN`, business list filtered when disabled +
      restored when re-enabled, business connect rejected while disabled, in-flight
      authorization still settles after disable. Payment suite untouched → 53/53 combined.

> **Known follow-ups:** structured audit event for platform toggles (currently
> `log.info` + `updated_by_username` only); surface backend `errorCode` in the admin
> toasts on the frontend. `db/baseline.sql` needs regenerating on the next greenfield
> prod bootstrap (new `platform_payment_provider_configs` table).

## Verify

```sh
./mvnw test
# isolated: ./mvnw test -Dtest=<Name>IntegrationTest
# dev boot against a fresh qliina_db:
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

## Prod notes

- Manual record for CARD/TRANSFER always works with zero provider config — this
  is the core path for most laundry businesses.
- Provider connections are optional per business: `DISCONNECTED` (default) /
  `PLATFORM` (platform subaccount) / `BYO` (business's own credentials, AES-GCM
  encrypted at rest, never returned to the client).
- Real platform provider keys stay env-only and fail closed (`application-prod.yml`,
  no committed defaults); the simulator is the only `configured` provider outside
  prod.
- Redirect/pending provider payments persist as `PENDING` and are excluded from
  paid totals; don't "simplify" back to `sumPaymentsByOrderId`.