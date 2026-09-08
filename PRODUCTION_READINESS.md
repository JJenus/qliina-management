# Qliina Management — Production Readiness Checklist

Ordered by effort (lightest first). Work in this order, merge independently, tick
items off here as they land, and keep the file in the repo so it survives
interruptions. Re-verify with the commands at the bottom.

## ✅ Done (verified)

- [x] **Order-domain IDOR + search** — `OrderService.getOwnedOrder(businessId, orderId)`
      guards every tenant-scoped order/orderitem/timeline/note operation
      (`BUSINESS_MISMATCH`/`ORDER_NOT_FOUND`), `OrderFilter.search` matches
      customer name/phone, tracking number, and order number via a
      tenant-scoped Customer subquery. Covered by `OrderIntegrationTest`
      (38 tests), incl. `crossTenant*` and `listOrders_search*`.
- [x] **Schema reconciliation (no Flyway)** — dropped all local DBs, generated
      `src/main/resources/db/baseline.sql` (`pg_dump --schema-only`) from the
      real entity-built schema, removed the broken/duplicate `db/migration/`
      set and the dead Flyway deps/plugin/config (Spring Boot 4 autoconfigure
      has no Flyway support, so it never ran at runtime). Boot with
      `ddl-auto=validate` against a baseline-imported DB verified clean.
      Greenfield bootstrap: `psql -h localhost -U root_user -d <db> -v
      ON_ERROR_STOP=1 -f src/main/resources/db/baseline.sql`.
- [x] **Rate limiting (whole backend + login brute-force)** — `RateLimitFilter`
      (bucket4j 7.6.0) per client IP: global budget on `/api/**` + stricter
      budget on `/api/v1/auth/**`; exhaust → 429 ProblemDetail
      `errorCode=RATE_LIMITED`. Config: `app.rate-limit.default-limit.*` /
      `app.rate-limit.login.*`. Per-account lockout (5 fail → 30 min) already
      in `AuthService`. Covered by `RateLimitIntegrationTest`.

- [x] **Secrets out of source** — `application-prod.yml` now resolves every
      secret (`JWT_SECRET`, `ENCRYPTION_SECRET`, `ENCRYPTION_SALT`,
      `BILLING_GATEWAY_SECRET`) with **no defaults** → prod fails boot with
      a clear placeholder error when any is missing (verified). `docker-compose.yml`
      requires `POSTGRES_PASSWORD` + `JWT_SECRET` via `${VAR:?...}` (no fallback);
      `.env.example` documents all required vars. Dev/test keep local defaults.

- [x] **Admin-bootstrap gating + reference-data seeding split** — the old
      monolithic `DataInitializer` (518 lines) is replaced by four small,
      focused runners:
      - `PermissionSeeder` (`@Order 1`) — permission catalog.
      - `RoleSeeder` (`@Order 2`) — system roles + idempotent top-ups.
      - `BillingPlanSeeder` (`@Order 3`) — FREE/STARTER/PRO plans.
      - `PlatformAdminSeeder` (`@Order 4`) — platform business + `admin`
        superadmin; gated by `app.seed-data.admin.enabled`
        (`${SEED_ADMIN_ENABLED:true}` base / `:false` prod) and requires
        `SEED_ADMIN_PASSWORD` (blank by default in prod → **fails fast**, never
        a committed credential; dev/test keep `Admin@123`).
      Reference data (permissions/roles/plans) is credential-free and always
      seeds in every profile, so prod never runs without required data; only
      the admin bootstrap is opt-in. `DevDataSeeder` moved to `@Order 10`.
      Verified: prod boots `validate` against a real DB and seeds
      roles/perms/plans with **no admin created**; `SEED_ADMIN_ENABLED=true`
      + password creates `admin` and `/api/v1/auth/login` returns 200;
      enabled-without-password fails fast. Full flow under "Prod boot contract".

- [x] **docker-compose profile** — app service now sets
      `SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-dev}` so the compose
      stack never boots the default `test` profile (`ddl-auto=create-drop` on
      H2 = data loss). `dev` (Postgres in-file, `update`) or `prod` (env-only
      secrets). Obsolete `version:` attribute dropped; `docker compose config`
      validates clean. Required `POSTGRES_PASSWORD`/`JWT_SECRET` fail fast
      without `.env`.

- [x] **Prometheus registry** — `micrometer-registry-prometheus` added (version
      via BOM); `/actuator/prometheus` now routes (auth-gated 401, not 404)
      under the existing `management.endpoints.web.exposure` list.

## ✅ Completed
- [x] **SDK/API stubs inventory** — notification channels (SMS/Push/WhatsApp) now
      **fail closed** unless `app.notification.channels.mock-external=true`;
      `uploads`/`attachments` return `ATTACHMENTS_UNSUPPORTED` instead of silently
      succeeding; CSV export hardened (see export item below). `SimulatedPaymentGateway`
      retained as the test/sandbox provider per the Payments spec.
- [x] **TOTP (2FA)** — `AuthService.verifyTOTP` implements RFC 6238 (HMAC-SHA1,
      30s step, ±1 window, constant-time compare); enrollment flips `totpEnabled`
      on first successful verify; login gates on 2FA. `AuthIntegrationTest` 38/38.
- [x] **WebSocket authorization** — `/ws/**` CONNECT requires a valid JWT on the
      STOMP CONNECT frame (rejects missing/invalid/expired outright); SUBSCRIBE
      is tenant-scoped (`/topic/business.{id}.*` only for the caller's business);
      user destinations use the canonical `/user` prefix, so `/user/queue/...` is
      scoped to the authenticated session principal. In-app notification delivery
      fixed (publisher now targets the username principal). `WebSocketAuthIntegrationTest`
      4/4.
- [x] **Export hardening** — CSV fields sanitized against spreadsheet formula
      injection; decimals written as strings. PDF switched from iText 7.2.5 (AGPL,
      stale) to **OpenPDF 2.0.3** + bouncycastle `bcprov-jdk18on`. `ReportingIntegrationTest`
      49/49.
- [x] **Unit test for rate-limit global path** — explicit low-limit global-bucket
      429 test covering backfill. `RateLimitGlobalIntegrationTest`/`RateLimitIntegrationTest`
      green.

- [x] **Payments: provider SPI + Flutterwave + Paystack (backend)** — `PaymentProvider`
      SPI (charge/settle/verify/refund/webhook; fail-closed) with **Simulator**,
      **Paystack** and **Flutterwave** adapters; per-business admin enable/disable
      (`payment_provider_configs`, either/both/none) via
      `GET|PATCH /api/v1/{businessId}/payment-providers[/{provider}/enabled]`.
      Checkout routing: CARD/TRANSFER require an enabled+configured provider
      (`PAYMENT_PROVIDER_REQUIRED`/`PROVIDER_UNKNOWN`/`PROVIDER_DISABLED`/
      `PROVIDER_NOT_CONFIGURED`); redirect providers persist `PENDING` payments
      (excluded from balance via `sumCompletedPaymentsByOrderId`); declined
      attempts return `FAILED` without persisting. Webhook reconcile
      `POST /api/v1/webhooks/payments/{provider}` (HMAC/`verif-hash` signature
      gate, `INVALID_WEBHOOK_SIGNATURE`) + `verify` endpoint settle the payment.
      Provider identity persisted on `order_payments`
      (`provider`/`provider_reference`/`provider_status`). Covered by
      `PaymentProviderIntegrationTest` (13 tests) + `PaymentIntegrationTest`
      (36, incl. CARD/TRANSFER legs). **Frontend checkout rendering** (selecting
      among enabled providers + admin gateway toggle) remains — tracked in
      `qliina-pwa/PRODUCTION_READINESS.md` "Payments UI".

## ⬜ Remaining (lightest first)
- [x] **Onboarding (first-run setup)** — `Business.setupRequired` (null
      `onboarding_completed_at`) surfaced on `BusinessDTO`/admin DTOs;
      idempotent `POST /api/v1/businesses/{businessId}/onboarding`
      (`admin.settings`) flips it; demo seed marks complete, legacy tenants stay
      clear. Frontend `/setup` wizard (services→garments→pricing→done), owner
      "Continue setup" banner, E2E walks the whole flow. `baseline.sql` column +
      `BusinessIntegrationTest` (29 tests).

## Verify

```sh
./mvnw test
# isolated: ./mvnw test -Dtest=<Name>IntegrationTest
# dev boot against a fresh qliina_db:
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
# greenfield bootstrap (after dropping the target DB):
psql -h localhost -U root_user -d <db> -v ON_ERROR_STOP=1 -f src/main/resources/db/baseline.sql
```

## Prod boot contract

- `ddl-auto=validate` (base + prod) — schema must equal the entity model /
  baseline.sql; if `validate` wakes up, regenerate `baseline.sql` from a fresh
  dev boot, don't hand-edit.
- Rate-limit budgets are env-tunable (`RATE_LIMIT_*`), keep them non-zero.
- No secrets inside the source tree in `prod` — every secret resolves from
  env with **no committed default**: `JWT_SECRET`, `ENCRYPTION_SECRET`,
  `ENCRYPTION_SALT`, `BILLING_GATEWAY_SECRET`. Boot fails fast if any is missing.
- **Greenfield bootstrap** (first boot on an empty DB):
  1. `psql ... -f src/main/resources/db/baseline.sql` (schema) or let
     `ddl-auto=update` create it once.
  2. **Reference data** (permissions, 11 system roles incl. `BUSINESS_ADMIN`,
     FREE/STARTER/PRO billing plans) seeds automatically on the first boot in
     every profile — credential-free, create-only/idempotent. No flag needed.
  3. **Admin** is a separate, one-time step (default off in prod):
     `SEED_ADMIN_ENABLED=true SEED_ADMIN_PASSWORD=<strong> ./mvnw spring-boot:run -Dspring-boot.run.profiles=prod`
     → creates the platform business + `admin` superadmin. Fails fast if the
     password is unset — never falls back to the committed dev default.
  4. Boot again with `SEED_ADMIN_ENABLED=false` (default) for all subsequent
     restarts; change `admin`'s password immediately after first use.
- Without the admin bootstrap (e.g. restoring a backup), tenant operations and
  registration work; platform/superadmin surfaces need the admin bootstrap.