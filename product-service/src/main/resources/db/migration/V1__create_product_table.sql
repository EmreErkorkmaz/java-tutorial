-- Flyway naming: V<version>__<description>.sql  (TWO underscores after the version)
-- Applied migrations are recorded in the flyway_schema_history table and are
-- checksum-verified on every startup: never edit a file that has already run.

CREATE TABLE product (
    id BIGINT GENERATED  BY DEFAULT AS IDENTITY PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    price NUMERIC(38, 2) NOT NULL,

    -- mirrors @Positive on the DTO: the API is not the only way rows get in here
    CONSTRAINT product_price_positive check (price > 0)
);