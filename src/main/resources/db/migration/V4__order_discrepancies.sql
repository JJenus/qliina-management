-- V4__order_discrepancies.sql
--
-- Discrepancy reporting (P5):
-- Workers report order-level problems (code mismatch, count off, damaged or
-- missing pieces) to front desk / managerial roles. Status workflow:
-- OPEN -> ACKNOWLEDGED -> RESOLVED | DISMISSED.

CREATE TABLE order_discrepancies (
    id             UUID PRIMARY KEY,
    created_at     TIMESTAMP NOT NULL,
    updated_at     TIMESTAMP,
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT,
    business_id    UUID NOT NULL,
    order_id       UUID NOT NULL,
    order_item_id  UUID,
    reported_by    UUID NOT NULL,
    type           VARCHAR(255) NOT NULL,
    description    TEXT NOT NULL,
    status         VARCHAR(255) NOT NULL,
    handled_by     UUID,
    handling_note  TEXT,
    resolved_at    TIMESTAMP,
    CONSTRAINT fk_discrepancies_business FOREIGN KEY (business_id) REFERENCES businesses (id),
    CONSTRAINT fk_discrepancies_order    FOREIGN KEY (order_id)    REFERENCES orders (id)
);

CREATE INDEX idx_discrepancies_business_status ON order_discrepancies (business_id, status);
CREATE INDEX idx_discrepancies_order           ON order_discrepancies (order_id);
CREATE INDEX idx_discrepancies_reported_by     ON order_discrepancies (reported_by);
CREATE INDEX idx_discrepancies_created         ON order_discrepancies (created_at);
