-- Per-item pipeline awareness: lets an item opt out of the washing stage
-- (e.g. "Ironing Only" garments go RECEIVED -> IRONING -> IRONED directly).
-- requires_washing is derived from the catalog category at order creation;
-- backfilled here from the closest matching service_types row by name.
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS service_type_id uuid;
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS requires_washing boolean NOT NULL DEFAULT TRUE;

UPDATE order_items oi
SET service_type_id = st.id,
    requires_washing = (st.category IS NULL OR st.category NOT IN ('IRON', 'DRY_CLEAN'))
FROM service_types st
WHERE oi.service_type_id IS NULL
  AND oi.business_id = st.business_id
  AND lower(trim(oi.service_type)) = lower(trim(st.name));
