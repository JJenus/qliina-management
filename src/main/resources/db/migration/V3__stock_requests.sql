-- V3__stock_requests.sql
--
-- Supply requests (P4):
-- Workers request restock of supplies (detergent, soap, hangers, ...).
-- Managerial roles review: PENDING -> APPROVED | REJECTED, then FULFILLED
-- once a purchase order / adjustment covers it. Workers may CANCEL while
-- still PENDING.

CREATE TABLE stock_requests (
    id              UUID PRIMARY KEY,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP,
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT,
    business_id     UUID NOT NULL,
    shop_id         UUID NOT NULL,
    item_id         UUID NOT NULL,
    requested_by    UUID NOT NULL,
    quantity        NUMERIC(10, 2) NOT NULL,
    urgency         VARCHAR(255) NOT NULL,
    notes           TEXT,
    status          VARCHAR(255) NOT NULL,
    reviewed_by     UUID,
    reviewed_at     TIMESTAMP,
    resolution_note TEXT,
    CONSTRAINT fk_stock_requests_business FOREIGN KEY (business_id) REFERENCES businesses (id),
    CONSTRAINT fk_stock_requests_item     FOREIGN KEY (item_id)     REFERENCES inventory_items (id)
);

CREATE INDEX idx_stock_requests_business_status ON stock_requests (business_id, status);
CREATE INDEX idx_stock_requests_shop            ON stock_requests (shop_id);
CREATE INDEX idx_stock_requests_requested_by    ON stock_requests (requested_by);
CREATE INDEX idx_stock_requests_created         ON stock_requests (created_at);
