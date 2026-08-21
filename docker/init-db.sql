-- Runs once, on a fresh data volume only. Each service owns its own database:
-- order-service must never read product-service's tables directly.
CREATE DATABASE orderdb OWNER product;
