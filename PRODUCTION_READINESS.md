# Qliina Management — Production Readiness Checklist

Ordered by effort (lightest first). Work in this order, merge independently, tick
items off here as they land, and keep the file in the repo so it survives
interruptions. Re-verify with the commands at the bottom.

**Current task: payment-acceptance re-scope.** Businesses must always be able to
record a card/transfer payment manually (external POS, direct bank transfer) —
**manual record is the always-on default and is never blocked, even when gateway
providers are configured.** Provider connections are additive, optional business
config. (Archived previous checklist: `archive/PRODUCTION_READINESS.md`.)

## ⬜ Phase 0 — Manual record restores the always-on core (de-regression)

- [ ] **Optional `provider` on payment** — `PaymentService.processPayment`:
      `provider` is optional for CARD/TRANSFER. Omitted → record the payment as
      `COMPLETED` immediately with the staff-entered reference
      (last4/authorization/txn id), same accounting path as CASH. Supplied →
      existing charge path unchanged (redirect/PENDING/webhook settle). Keep the
      `PROVIDER_UNKNOWN` / `PROVIDER_DISABLED` / `PROVIDER_NOT_CONFIGURED` 400s
      **only** when a provider is actually supplied — never silently downgrade a
      business that picked a gateway.
- [ ] **Split legs** — `PaymentService.splitPayment` uses the same optional-
      provider rule per leg; fix the declined-leg double-counting bug so only
      successful legs count toward paid totals.
- [ ] **Invariant** — manual record always available for CARD/TRANSFER with zero
      provider config; provider presence only *adds* gateway options.
- [ ] **Tests** — flip `PaymentProviderIntegrationTest.processPayment_cardWithoutProvider`
      (was 400 `PAYMENT_PROVIDER_REQUIRED`) → `COMPLETED` + order settled; add a
      no-provider CARD = fully-paid integration case. Keep existing
      disabled/unknown/unconfigured 400 tests; add a split case with a no-provider
      leg. Full `./mvnw test` green.

## ⬜ Phase 1 — Connection model (advanced per-business config)

- [ ] **`PaymentProviderConfig` extension** — `{provider, enabled}` grows to
      `connectionMode` (`DISCONNECTED` | `PLATFORM` | `BYO`), `credentialsEncrypted`
      (AES-GCM via `EncryptionService`, decrypted only inside the payment service),
      and `platformSubaccountId`. Unique `(business_id, provider)` kept. Manual is
      orthogonal — never represented as a provider state; `DISCONNECTED` simply
      means "no gateway".
- [ ] **SPI per-connection** — `PaymentProvider` gains
      `supportsPlatformSubaccounts()` and the charge path carries the per-business
      connection context (subaccount ref or BYO credentials). `available` for a
      business = `PLATFORM` with the platform flag on, or `BYO` with encrypted
      credentials present; both fail closed otherwise.
- [ ] **Config surface** — `PaymentProviderDTO` gains `connectionMode` +
      `hasCredentials` (never the secret). `PUT /api/v1/{businessId}/payment-providers/{name}/connection`
      accepts `{mode, secretKey?}`; BYO secrets are encrypted at rest and never
      returned to the client.
- [ ] **Simulator stands in** for both `PLATFORM` (simulated subaccount) and
      `BYO` in dev/test; real Paystack/Flutterwave platform keys deferred until
      provider-side platform/subaccount APIs are confirmed.
- [ ] **Tests** — connection modes persist per business; BYO never surfaces in
      responses; misconfigured/unconfigured connections fail closed; list +
      connection-toggle stay un-clock-gated (settings surface).

## ⬜ Phase 2 — Generate payment, scan/QR, webhook auto-link

- [ ] **Generate payment** — endpoint creates a `PENDING` `OrderPayment`
      (amount/method/provider) and returns a customer checkout URL + QR payload
      (Paystack `authorization_url` / Flutterwave link; simulator `redirectMode`).
      Excluded from paid totals via
      `OrderPaymentRepository.sumCompletedPaymentsByOrderId` (`COMPLETED` only).
- [ ] **Webhook auto-link hardening** — `PaymentProviderService.processWebhook`
      matches by provider + reference **and cross-checks amount** before settle;
      `charge.failed` events transition the payment to `FAILED`.
- [ ] **Unmatched funds queue** — incoming funds with no matching pre-generated
      `OrderPayment` (e.g. plain transfer to a BYO account) create a
      staff-reconciliation item instead of being dropped.
- [ ] **Pending/verify surface** — `PENDING` list for an order + recheck via the
      existing verify endpoint (`PaymentProviderController`).

## ⬜ Phase 3 — Ops hardening

- [ ] **PENDING sweep** — scheduled expiry/cleanup of abandoned PENDING payments.
- [ ] **Charge idempotency** — single active `PENDING` per order+provider to
      prevent duplicate charges.
- [ ] **Provider refund parity** — `refund` routed through the business's own
      connection (subaccount / BYO credentials).

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