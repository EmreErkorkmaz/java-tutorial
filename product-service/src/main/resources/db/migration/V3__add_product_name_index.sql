-- findByName() currently forces a full table scan. A B-tree index on name turns the
-- lookup into a direct descent instead of reading every row.
CREATE INDEX idx_product_name ON product (name);