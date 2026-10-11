-- Hibernate maps String to VARCHAR; CHAR(3) is bpchar and fails ddl-auto=validate.
ALTER TABLE products ALTER COLUMN currency TYPE VARCHAR(3);
ALTER TABLE orders ALTER COLUMN currency TYPE VARCHAR(3);
