-- The order list filters by user_id and sorts by created_at DESC. One composite index
-- serves both: Postgres walks that user's entries already in the right order and never
-- has to sort the result in memory.
--
-- Column order matters: the equality filter comes first, the sort column second.
CREATE INDEX idx_order_user_created ON customer_order (user_id, created_at DESC);

-- Redundant now. An index on (user_id, created_at) also answers lookups by user_id alone -
-- leftmost columns can be used on their own - so keeping both would only cost write time.
DROP INDEX idx_order_user_id;
