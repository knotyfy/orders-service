CREATE TABLE products (
    id UUID PRIMARY KEY,
    sku VARCHAR(64) NOT NULL UNIQUE,
    name TEXT NOT NULL,
    unit_price NUMERIC(19, 2) NOT NULL CHECK (unit_price >= 0),
    currency CHAR(3) NOT NULL,
    stock INTEGER NOT NULL CHECK (stock >= 0),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE orders (
    id UUID PRIMARY KEY,
    customer_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN ('PENDING_PAYMENT', 'PAID', 'CANCELLED')),
    currency CHAR(3) NOT NULL,
    total_amount NUMERIC(19, 2) NOT NULL CHECK (total_amount >= 0),
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    payment_attempts INTEGER NOT NULL DEFAULT 0
        CHECK (payment_attempts >= 0 AND payment_attempts <= 3),
    payment_claimed_at TIMESTAMPTZ,
    payment_claimed_by VARCHAR(128),
    expires_at TIMESTAMPTZ NOT NULL,
    cancelled_reason VARCHAR(32)
        CHECK (cancelled_reason IS NULL OR cancelled_reason IN ('PAYMENT_FAILED', 'EXPIRED')),
    restocked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (customer_id, idempotency_key)
);

CREATE INDEX idx_orders_status_expires ON orders (status, expires_at);
CREATE INDEX idx_orders_status_claim ON orders (status, payment_claimed_at, payment_attempts);

CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products (id),
    sku VARCHAR(64) NOT NULL,
    product_name TEXT NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(19, 2) NOT NULL CHECK (unit_price >= 0),
    UNIQUE (order_id, product_id)
);

CREATE TABLE payment_attempts (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    attempt_number INTEGER NOT NULL CHECK (attempt_number > 0),
    status VARCHAR(16) NOT NULL CHECK (status IN ('CLAIMED', 'SUCCESS', 'FAILURE')),
    claimed_at TIMESTAMPTZ NOT NULL,
    UNIQUE (order_id, attempt_number)
);
