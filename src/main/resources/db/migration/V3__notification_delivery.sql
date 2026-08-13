-- V3__notification_delivery.sql
--
-- Notification delivery split described in notification-system-db-design.md:
--   notifications (hub) --< notification_deliveries --< notification_delivery_events
-- plus a durable transactional outbox (notification_outbox) that decouples the
-- request path from provider dispatch.
--
-- Conventions match V2__billing_schema.sql and the JPA entities that are
-- validated by spring.jpa.hibernate.ddl-auto=validate: every row carries the
-- BaseEntity audit columns (created_at/updated_at/created_by/updated_by/version)
-- and, for tenant tables, business_id/shop_id from BaseTenantEntity.

-- ---------------------------------------------------------------------------
-- Deliveries: one row per (hub notification x enabled channel). Retry state
-- lives here (not on notifications) so a retry job can query it directly:
--   WHERE status='FAILED' AND attempt_count < max_attempts AND next_retry_at <= now()
-- ---------------------------------------------------------------------------
CREATE TABLE notification_deliveries (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id      UUID NOT NULL,
    shop_id          UUID,
    notification_id  UUID NOT NULL REFERENCES notifications (id),
    channel          VARCHAR(20) NOT NULL,          -- IN_APP | EMAIL | SMS | WHATSAPP | PUSH
    device_id        UUID,                          -- set for push deliveries
    recipient        VARCHAR(255),                  -- email / phone snapshot at dispatch time
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- pending | sent | failed | exhausted
    attempt_count    INTEGER NOT NULL DEFAULT 0,
    max_attempts     INTEGER NOT NULL DEFAULT 5,
    next_retry_at    TIMESTAMP,
    last_error       TEXT,
    last_error_type  VARCHAR(20),                   -- transient | permanent
    sent_at          TIMESTAMP,
    delivered_at     TIMESTAMP,
    created_at       TIMESTAMP NOT NULL,
    updated_at       TIMESTAMP,
    created_by       UUID,
    updated_by       UUID,
    version          BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_notif_delivery_notification ON notification_deliveries (notification_id);
CREATE INDEX idx_notif_delivery_retry ON notification_deliveries (status, next_retry_at);
CREATE INDEX idx_notif_delivery_business ON notification_deliveries (business_id);

-- ---------------------------------------------------------------------------
-- Append-only delivery event stream: sent -> delivered -> opened -> clicked / failed.
-- ---------------------------------------------------------------------------
CREATE TABLE notification_delivery_events (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id  UUID NOT NULL,
    shop_id      UUID,
    delivery_id  UUID NOT NULL REFERENCES notification_deliveries (id),
    event_type   VARCHAR(20) NOT NULL,              -- sent | delivered | opened | clicked | failed
    occurred_at  TIMESTAMP NOT NULL,
    detail       TEXT,
    created_at   TIMESTAMP NOT NULL,
    updated_at   TIMESTAMP,
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_notif_delivery_event_delivery ON notification_delivery_events (delivery_id);
CREATE INDEX idx_notif_delivery_event_time ON notification_delivery_events (occurred_at);

-- ---------------------------------------------------------------------------
-- Transactional outbox: durable queue decoupling notification creation +
-- provider dispatch from the request path. Claimed with a lease
-- (locked_at/locked_by); rows whose lease expires are re-picked (queue-level
-- redelivery). Exhausted rows are parked as DEAD for manual inspection (DLQ).
-- ---------------------------------------------------------------------------
CREATE TABLE notification_outbox (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id    UUID NOT NULL,
    shop_id        UUID,
    user_id        UUID,                            -- recipient; NULL for broadcast
    template_id    UUID,                            -- NULL for ad-hoc sends
    type           VARCHAR(20) NOT NULL,            -- NotificationType
    channel        VARCHAR(20),                     -- hint channel; NULL when mandatory
    mandatory      BOOLEAN NOT NULL DEFAULT FALSE,  -- bypasses preference check (doc §3)
    priority       VARCHAR(20),
    title          VARCHAR(255),
    body           TEXT,
    data           JSONB,
    scheduled_for  TIMESTAMP,
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- pending | processed | dead
    attempt_count  INTEGER NOT NULL DEFAULT 0,
    max_attempts   INTEGER NOT NULL DEFAULT 10,
    next_attempt_at TIMESTAMP,
    locked_at      TIMESTAMP,
    locked_by      UUID,
    processed_at   TIMESTAMP,
    last_error     TEXT,
    created_at     TIMESTAMP NOT NULL,
    updated_at     TIMESTAMP,
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_notif_outbox_claim ON notification_outbox (status, next_attempt_at);
CREATE INDEX idx_notif_outbox_schedule ON notification_outbox (scheduled_for);
CREATE INDEX idx_notif_outbox_business ON notification_outbox (business_id);

-- ---------------------------------------------------------------------------
-- Additive changes to existing entity-managed tables.
-- ---------------------------------------------------------------------------
ALTER TABLE notifications ADD COLUMN template_id UUID;
CREATE INDEX idx_notifications_template ON notifications (template_id);

ALTER TABLE notification_templates ADD COLUMN is_mandatory BOOLEAN NOT NULL DEFAULT FALSE;
