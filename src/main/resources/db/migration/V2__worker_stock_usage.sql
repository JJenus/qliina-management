-- V2__worker_stock_usage.sql
--
-- Stock usage logging (P3):
-- Workers log consumables used while processing orders. Usage is recorded as
-- a stock_transactions row with type = 'USED' (performed_by = the worker),
-- optionally attributed to the order / order item being processed.

ALTER TABLE stock_transactions ADD COLUMN order_id      UUID;
ALTER TABLE stock_transactions ADD COLUMN order_item_id UUID;

CREATE INDEX idx_stock_txn_order ON stock_transactions (order_id);
