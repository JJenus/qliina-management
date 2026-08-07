-- V2__billing_schema.sql
--
-- Production-grade subscription/billing schema described in
-- subscription-system-db-design.md. All tables are prefixed with `billing_`
-- to avoid collisions with the order-payment module (`invoices`,
-- `payment_methods`, `invoice_items`).
--
-- Money columns are NUMERIC(12,2) — never float.
-- Every entity extends common/BaseEntity (created_at, updated_at, created_by,
-- updated_by, version) which is validated by spring.jpa.hibernate.ddl-auto=validate.
--
-- Idempotency: unique indexes on invoices/payments idempotency_key and on
-- (gateway, gateway_transaction_id) per §8.

-- ---------------------------------------------------------------------------
-- Plans (soft-deleted; never hard-deleted while referenced — §5)
-- ---------------------------------------------------------------------------
CREATE TABLE billing_plans (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(120) NOT NULL,
    description TEXT,
    status      VARCHAR(20)  NOT NULL,          -- active | deprecated | archived
    archived_at TIMESTAMP,
    created_at  TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP,
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_billing_plans_status ON billing_plans (status);

CREATE TABLE billing_plan_versions (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id        UUID NOT NULL REFERENCES billing_plans (id),
    price          NUMERIC(12,2) NOT NULL,
    currency       VARCHAR(3) NOT NULL DEFAULT 'NGN',
    effective_from TIMESTAMP NOT NULL,
    created_at     TIMESTAMP NOT NULL,
    updated_at     TIMESTAMP,
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_billing_plan_version UNIQUE (plan_id, effective_from)
);

CREATE INDEX idx_billing_plan_versions_plan ON billing_plan_versions (plan_id);

CREATE TABLE billing_plan_features (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id       UUID NOT NULL REFERENCES billing_plans (id),
    feature_key   VARCHAR(64) NOT NULL,
    feature_value VARCHAR(255) NOT NULL,
    is_hard_limit BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP NOT NULL,
    updated_at    TIMESTAMP,
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_billing_plan_feature UNIQUE (plan_id, feature_key)
);

CREATE INDEX idx_billing_plan_features_plan ON billing_plan_features (plan_id);

-- ---------------------------------------------------------------------------
-- Subscriptions — the hub of the schema (§2)
-- ---------------------------------------------------------------------------
CREATE TABLE billing_subscriptions (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id          UUID NOT NULL,
    plan_id              UUID NOT NULL REFERENCES billing_plans (id),
    plan_version_id      UUID NOT NULL REFERENCES billing_plan_versions (id),
    status               VARCHAR(20) NOT NULL,  -- trialing | active | past_due | canceled
    current_period_start TIMESTAMP,
    current_period_end   TIMESTAMP,
    trial_ends_at        TIMESTAMP,
    cancel_at_period_end BOOLEAN NOT NULL DEFAULT FALSE,
    retry_count          INTEGER NOT NULL DEFAULT 0,
    next_retry_at        TIMESTAMP,
    pending_plan_id      UUID REFERENCES billing_plans (id),
    pending_change_at    TIMESTAMP,
    created_at           TIMESTAMP NOT NULL,
    updated_at           TIMESTAMP,
    created_by           UUID,
    updated_by           UUID,
    version              BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_billing_subs_business   ON billing_subscriptions (business_id);
CREATE INDEX idx_billing_subs_status     ON billing_subscriptions (status);
CREATE INDEX idx_billing_subs_period_end ON billing_subscriptions (current_period_end);
CREATE INDEX idx_billing_subs_next_retry ON billing_subscriptions (next_retry_at);

CREATE TABLE billing_subscription_features (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id UUID NOT NULL REFERENCES billing_subscriptions (id),
    feature_key    VARCHAR(64) NOT NULL,
    feature_value  VARCHAR(255) NOT NULL,
    overridden_at  TIMESTAMP,
    created_at     TIMESTAMP NOT NULL,
    updated_at     TIMESTAMP,
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_billing_subscription_feature UNIQUE (subscription_id, feature_key)
);

CREATE INDEX idx_billing_sub_features_sub ON billing_subscription_features (subscription_id);

CREATE TABLE billing_subscription_coupons (
    subscription_id UUID NOT NULL REFERENCES billing_subscriptions (id),
    coupon_id       UUID NOT NULL REFERENCES billing_coupons (id),
    created_at      TIMESTAMP NOT NULL,
    PRIMARY KEY (subscription_id, coupon_id)
);

CREATE INDEX idx_billing_sub_coupons_coupon ON billing_subscription_coupons (coupon_id);

-- Append-only transition ledger (§6) — the churn/analytics query source.
CREATE TABLE billing_subscription_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id UUID NOT NULL REFERENCES billing_subscriptions (id),
    from_status     VARCHAR(20),                 -- null for the initial creation event
    to_status       VARCHAR(20) NOT NULL,
    reason          TEXT,
    occurred_at     TIMESTAMP NOT NULL,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP,
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_billing_sub_events_sub ON billing_subscription_events (subscription_id);

-- ---------------------------------------------------------------------------
-- Invoicing — append-only ledger of line items (§7)
-- ---------------------------------------------------------------------------
CREATE TABLE billing_invoices (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id UUID NOT NULL REFERENCES billing_subscriptions (id),
    invoice_number  VARCHAR(40) NOT NULL UNIQUE,
    idempotency_key VARCHAR(255) UNIQUE,
    amount          NUMERIC(12,2) NOT NULL,
    status          VARCHAR(20) NOT NULL,       -- draft | open | paid | failed
    issued_at       TIMESTAMP NOT NULL,
    due_at          TIMESTAMP,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP,
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_billing_invoices_sub ON billing_invoices (subscription_id);
CREATE INDEX idx_billing_invoices_status ON billing_invoices (status);

CREATE TABLE billing_invoice_line_items (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id    UUID NOT NULL REFERENCES billing_invoices (id),
    type          VARCHAR(30) NOT NULL,          -- subscription | proration_credit | proration_charge | one_off | tax
    description   TEXT,
    amount        NUMERIC(12,2) NOT NULL,        -- negative for credits
    period_start  TIMESTAMP,
    period_end    TIMESTAMP,
    created_at    TIMESTAMP NOT NULL,
    updated_at    TIMESTAMP,
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_billing_line_items_invoice ON billing_invoice_line_items (invoice_id);

-- ---------------------------------------------------------------------------
-- Payments — gateway reconciliation (§8)
-- ---------------------------------------------------------------------------
CREATE TABLE billing_payments (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id            UUID NOT NULL REFERENCES billing_invoices (id),
    payment_method_id     UUID REFERENCES billing_payment_methods (id),
    idempotency_key       VARCHAR(255) UNIQUE,
    gateway               VARCHAR(30) NOT NULL,
    gateway_transaction_id VARCHAR(128) NOT NULL,
    gateway_response      TEXT,
    amount                NUMERIC(12,2) NOT NULL,
    fee_amount            NUMERIC(12,2) NOT NULL DEFAULT 0,
    net_amount            NUMERIC(12,2) NOT NULL DEFAULT 0,
    status                VARCHAR(20) NOT NULL,  -- pending | approved | failed | refunded | disputed
    paid_at               TIMESTAMP,
    created_at            TIMESTAMP NOT NULL,
    updated_at            TIMESTAMP,
    created_by            UUID,
    updated_by            UUID,
    version               BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_billing_payment_txn UNIQUE (gateway, gateway_transaction_id)
);

CREATE INDEX idx_billing_payments_invoice ON billing_payments (invoice_id);

CREATE TABLE billing_payment_methods (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id        UUID NOT NULL,
    type               VARCHAR(20) NOT NULL,     -- card | bank
    is_default         BOOLEAN NOT NULL DEFAULT FALSE,
    gateway_customer_id VARCHAR(128),
    gateway_method_id  VARCHAR(128),
    label              VARCHAR(120),
    created_at         TIMESTAMP NOT NULL,
    updated_at         TIMESTAMP,
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_billing_payment_methods_business ON billing_payment_methods (business_id);

-- ---------------------------------------------------------------------------
-- Metered usage (§2 usage_records)
-- ---------------------------------------------------------------------------
CREATE TABLE billing_usage_records (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id UUID NOT NULL REFERENCES billing_subscriptions (id),
    feature_key     VARCHAR(64) NOT NULL,
    quantity        NUMERIC(12,2) NOT NULL,
    recorded_at     TIMESTAMP NOT NULL,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP,
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_billing_usage_sub_key ON billing_usage_records (subscription_id, feature_key, recorded_at);

-- ---------------------------------------------------------------------------
-- Coupons — redemptions_count is a cache; coupon_redemptions is the ledger (§4)
-- ---------------------------------------------------------------------------
CREATE TABLE billing_coupons (
    id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                       VARCHAR(40) NOT NULL UNIQUE,
    discount_type              VARCHAR(20) NOT NULL,   -- percent | fixed_amount
    discount_value             NUMERIC(12,2) NOT NULL,
    max_redemptions            INTEGER,
    redemptions_count          INTEGER NOT NULL DEFAULT 0,
    max_redemptions_per_business INTEGER,
    starts_at                  TIMESTAMP,
    expires_at                 TIMESTAMP,
    status                     VARCHAR(20) NOT NULL,   -- active | disabled | expired
    created_at                 TIMESTAMP NOT NULL,
    updated_at                 TIMESTAMP,
    created_by                 UUID,
    updated_by                 UUID,
    version                    BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_billing_coupons_status ON billing_coupons (status);

CREATE TABLE billing_coupon_redemptions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    coupon_id       UUID NOT NULL REFERENCES billing_coupons (id),
    business_id     UUID NOT NULL,
    subscription_id UUID REFERENCES billing_subscriptions (id),
    redeemed_at     TIMESTAMP NOT NULL,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP,
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_billing_coupon_red_coupon ON billing_coupon_redemptions (coupon_id, business_id);

-- ---------------------------------------------------------------------------
-- Invoice number counter (sequential INV-YYYY-000123 allocation, §2 invoices)
-- ---------------------------------------------------------------------------
CREATE TABLE billing_invoice_counters (
    counter_year INTEGER PRIMARY KEY,
    next_value   BIGINT NOT NULL DEFAULT 1
);
