-- WIDGET-1: last-unit race fixture. WIDGET-10: happy path. FAIL-CARD: mock payment always fails.
INSERT INTO products (id, sku, name, unit_price, currency, stock, version) VALUES
    ('11111111-1111-1111-1111-111111111111', 'WIDGET-1', 'Single-stock widget', 10.00, 'USD', 1, 0),
    ('22222222-2222-2222-2222-222222222222', 'WIDGET-10', 'Ten-stock widget', 5.00, 'USD', 10, 0),
    ('33333333-3333-3333-3333-333333333333', 'FAIL-CARD', 'Payment always fails', 1.00, 'USD', 10, 0);
