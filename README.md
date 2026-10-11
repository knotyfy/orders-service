# Orders Service

Spring Boot 3 / Java 21 / PostgreSQL order API. Stock is reserved when the order is created. Orders start in `PENDING_PAYMENT`. One background poller either charges a **mock** gateway or expires unpaid orders. Concurrency tests are the grade.

This README is the contract. Code must match these rules, not informal chat decisions.

---

## Folder structure

```
Orders-Service/
├── README.md
├── pom.xml
├── docker-compose.yml
├── .gitignore
└── src/
    ├── main/
    │   ├── java/com/orders/service/
    │   │   ├── OrdersServiceApplication.java
    │   │   ├── api/                 # REST controllers + DTOs
    │   │   ├── config/              # Clock, orders.* properties
    │   │   ├── domain/              # Product, Order, OrderItem, PaymentAttempt
    │   │   ├── error/               # ProblemDetail handler + error codes
    │   │   ├── job/                 # Poller: expire unpaid, then claim/charge
    │   │   ├── payment/             # Mock PaymentGateway
    │   │   ├── repo/                # Spring Data repositories
    │   │   └── service/             # Create order + restock / payment
    │   └── resources/
    │       ├── application.yml
    │       └── db/migration/
    └── test/
        ├── sample-requests/         # HTTP request/response samples
        └── java/com/orders/service/ # Integration tests
```

Packages stay flat (controller / service / domain / repo / job). No hexagonal ports, no Kafka.

---

## Code modules (what each class does)

Request flow: **controller → service → repository → PostgreSQL**. The poller is a separate loop: **job → fulfillment service → payment gateway**.

### Boot and config

| Class | Role |
|---|---|
| `OrdersServiceApplication` | Starts the app and turns on scheduling. |
| `OrdersProperties` | Reads `orders.*` settings (timeout, max payment attempts, job interval). |
| `ClockConfig` | Provides a UTC clock so timestamps and expiry use the same time. |

### HTTP API (`api`)

| Class | Role |
|---|---|
| `OrderController` | `POST /orders` (create / replay) and `GET /orders/{id}`. |
| `ProductController` | `GET /products/{sku}` — current price and stock. |
| `CreateOrderRequest` / `OrderItemRequest` | Incoming JSON (customer + SKU/qty). No prices from the client. |
| `OrderResponse` / `OrderItemResponse` / `ProductResponse` | Outgoing JSON. |
| `CreatedOrder` | Tells the controller if this POST was new (201) or an idempotent replay (200). |

### Business logic (`service`)

| Class | Role |
|---|---|
| `OrderService` | Create path: validate, lock stock, snapshot prices, decrement, save `PENDING_PAYMENT`. Also get order/product. |
| `RequestHash` | SHA-256 of the create body so the same Idempotency-Key with a different payload is rejected. |
| `OrderFulfillmentService` | Poller work: expire unpaid orders, or claim then charge; restock once on cancel. |

### Background job (`job`)

| Class | Role |
|---|---|
| `OrderFulfillmentJob` | Timer: first expire due unpaid orders, then charge remaining pending ones. Tests call `run()` directly. |

### Payment (`payment`)

| Class | Role |
|---|---|
| `PaymentGateway` | Interface for charging an order. |
| `MockPaymentGateway` | Fake PSP: fails `FAIL-*` SKUs and `customer-fail`; otherwise succeeds. Counts charges for tests. |
| `PaymentRequest` / `PaymentResult` | Charge input and SUCCESS/FAILURE. |

### Domain (`domain`) — database entities

| Class | Role |
|---|---|
| `Product` | Catalog row: SKU, price, live stock. |
| `Order` | The order: status, totals, idempotency, payment claim, expiry. |
| `OrderItem` | Line frozen at create (SKU, name, qty, unit price). |
| `PaymentAttempt` | One gateway try; unique per order+attempt so two workers cannot double-charge. |
| `OrderStatus` | `PENDING_PAYMENT`, `PAID`, `CANCELLED`. |
| `CancelReason` | `PAYMENT_FAILED` or `EXPIRED`. |
| `PaymentAttemptStatus` | `CLAIMED`, `SUCCESS`, `FAILURE`. |

### Persistence (`repo`)

| Class | Role |
|---|---|
| `ProductRepository` | Load by SKU; `SELECT FOR UPDATE` products in SKU order (avoids deadlocks). |
| `OrderRepository` | Find order, advisory lock for duplicate POST, expire/claim SQL updates. |
| `PaymentAttemptRepository` | Saves attempt rows. |

### Errors (`error`)

| Class | Role |
|---|---|
| `ErrorCode` | Stable codes like `INSUFFICIENT_STOCK`, `VALIDATION_ERROR`. |
| `ApiException` | Thrown by services with an HTTP status + code. |
| `GlobalExceptionHandler` | Turns exceptions into `application/problem+json`. |

### Schema (`db/migration`)

| File | Role |
|---|---|
| `V1__init.sql` | Tables and constraints. |
| `V2__seed_products.sql` | Seed SKUs: `WIDGET-1`, `WIDGET-10`, `FAIL-CARD`. |
| `V3__currency_varchar.sql` | Aligns currency columns with Hibernate (`VARCHAR(3)`). |

### Tests

| Class | Role |
|---|---|
| `AbstractOrdersIT` | Shared Postgres container, reset stock, HTTP helpers. |
| `OrderConcurrencyIT` | Last-unit race, bad qty, insufficient stock, duplicate POST. |
| `PaymentJobIT` | No double-pay; three failures then cancel + restock. |
| `OrderExpiryIT` | Unpaid expires; PAID never expires. |
| `JobVsApiRaceIT` | Expire vs claim on the same order — one winner. |

---

## States

```
[*] → PENDING_PAYMENT  (POST /orders; stock already decremented)
PENDING_PAYMENT → PAID         (payment job SUCCESS)
PENDING_PAYMENT → CANCELLED    (attempts exhausted OR timeout)
PAID, CANCELLED are terminal
```

| Status | Stock | Notes |
|---|---|---|
| `PENDING_PAYMENT` | Held (already decremented) | Awaiting mock payment or `expiresAt` |
| `PAID` | Held forever | Never expire, never restock |
| `CANCELLED` | Restored **once** (`restocked` flag) | `cancelledReason`: `PAYMENT_FAILED` or `EXPIRED` |

There is no `PROCESSING` / `SHIPPED` / `DELIVERED`. There is no customer cancel API; unpaid abandon is the timeout path.

---

## Stock-at-create (`POST /api/v1/orders`)

Client never sends prices. Catalog is source of truth.

1. Validate body and required `Idempotency-Key` header.
2. Resolve products by SKU; unknown SKU → `PRODUCT_NOT_FOUND`.
3. Take a transaction-scoped advisory lock on `(customerId, Idempotency-Key)` so duplicate POSTs cannot double-decrement.
4. If that key already exists: same body hash → return the original order (200); different hash → `IDEMPOTENCY_CONFLICT`.
5. `SELECT … FOR UPDATE` products in **ascending SKU order** (deadlock avoidance).
6. If any `stock < qty` → `INSUFFICIENT_STOCK`; no partial decrement.
7. Snapshot `sku`, `productName`, `unitPrice`, `currency` onto line items; set `totalAmount`.
8. Decrement stock.
9. Persist `PENDING_PAYMENT`, `paymentAttempts = 0`, `expiresAt = now + timeout`.
10. Commit. Response 201 + `Location`.

Reject empty item list, quantity ≤ 0, and duplicate SKUs in one payload (`VALIDATION_ERROR`). Do not merge quantities.

---

## Timeout

- Default **15 minutes** from create (`orders.payment-timeout`). Tests override or freeze `Clock`.
- Only rows with `status = PENDING_PAYMENT` **and** `expiresAt <= now` expire.
- **Never expire `PAID`.**
- Expiry uses the same restock method as payment failure (`UPDATE … restocked = TRUE WHERE restocked = FALSE`, then increment product stock).

---

## Mock payment rules

In-process `PaymentGateway`. No Stripe, no HTTP.

| Input | Result |
|---|---|
| `customerId = customer-fail` | always `FAILURE` |
| any line SKU starts with `FAIL-` | always `FAILURE` |
| otherwise | `SUCCESS` |

- Max attempts: **3**. Then `CANCELLED` + restock, `cancelledReason = PAYMENT_FAILED`.
- **Claim before charge:** conditional `UPDATE` sets `payment_claimed_at` only if `PENDING_PAYMENT` and not already claimed. If the update matches 0 rows, skip. Call `charge()` only after a successful claim.
- Unique `(order_id, attempt_number)` on `payment_attempts` is the DB backstop against double-pay.
- Same poller: expire due unpaid first, then claim/charge remaining `PENDING_PAYMENT`.

---

## Error codes

Responses use RFC 7807 `application/problem+json` with a `code` property.

| `code` | HTTP | When |
|---|---|---|
| `VALIDATION_ERROR` | 400 | Empty items, qty ≤ 0, duplicate SKU in request, missing `customerId` or `Idempotency-Key` |
| `PRODUCT_NOT_FOUND` | 404 | SKU not in catalog |
| `ORDER_NOT_FOUND` | 404 | Unknown order id |
| `INSUFFICIENT_STOCK` | 409 | Any line exceeds current stock |
| `IDEMPOTENCY_CONFLICT` | 409 | Same key, different body |
| `CONCURRENT_UPDATE` | 409 | Claim/version lost (optional) |

Duplicate POST with the **same** key and **same** body hash: **200** and the original order. Stock decremented once.

---

## API (v1)

| Method | Path | Notes |
|---|---|---|
| `POST` | `/api/v1/orders` | Header `Idempotency-Key`. Body `{ customerId, items: [{ sku, quantity }] }` |
| `GET` | `/api/v1/orders/{orderId}` | Items, status, totals, attempts, `expiresAt` |
| `GET` | `/api/v1/products/{sku}` | Catalog price and **current** stock (proves decrement/restock) |
| `GET` | `/actuator/health` | Liveness of the app / DB |

Identity: `customerId` on the payload. No Spring Security.

Copy-paste samples (happy path and errors) live in [`src/test/sample-requests/`](src/test/sample-requests/00-index.txt).

---

## Schema constraints (Slice 1)

- `products.sku` unique; `stock >= 0`; `unit_price >= 0`; money `NUMERIC(19,2)`; `currency VARCHAR(3)`.
- `orders.status` in `PENDING_PAYMENT | PAID | CANCELLED`.
- Unique `(customer_id, idempotency_key)`.
- `order_items.quantity > 0`; unique `(order_id, product_id)`; prices are snapshots.
- `payment_attempts` unique `(order_id, attempt_number)`.
- Indexes: `(status, expires_at)` and `(status, payment_claimed_at, payment_attempts)`.

Seed SKUs (intended): `WIDGET-1` (stock 1), `WIDGET-10` (stock 10), `FAIL-CARD` (always fails payment).

---

## Tests that grade this service

Run against Testcontainers PostgreSQL, not H2.

1. **Last-unit race** — stock=1, two parallel POSTs qty=1, different keys → one 201, one 409, stock=0, one order.
2. **Insufficient stock** — stock=2, qty=3 → 409, stock still 2.
3. **Invalid qty** — 0 / negative / empty items → 400, no order row, stock unchanged.
4. **Duplicate POST** — same key + same body (serial and overlapping) → one reservation, same `orderId`.
5. **Payment job** — overlapping ticks charge **once**; three failures on `FAIL-*` → `CANCELLED` + restock.
6. **Expiry** — unpaid past `expiresAt` cancels + restocks; `PAID` never expires.
7. **Job vs API (polish)** — expire vs claim on the same unpaid order: exactly one of `PAID` or `CANCELLED`; never PAID+restock.

---

## Runbook

```bash
docker compose up -d
# wait until postgres is healthy
mvn spring-boot:run
```

```bash
# catalog
curl -s localhost:8080/api/v1/products/WIDGET-1

# create (reserves 1 unit, PENDING_PAYMENT)
curl -s -X POST localhost:8080/api/v1/orders \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: demo-1' \
  -d '{"customerId":"cust-1","items":[{"sku":"WIDGET-1","quantity":1}]}'

# replay — same order, stock still 0
curl -s -X POST localhost:8080/api/v1/orders \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: demo-1' \
  -d '{"customerId":"cust-1","items":[{"sku":"WIDGET-1","quantity":1}]}'
```

Job interval defaults to a few seconds (`orders.job.interval-ms`). Tests disable the scheduler and invoke the poller method directly.

```bash
mvn test   # requires Docker for Testcontainers
```

---

## Out of scope

- Auth, JWT, OAuth
- Kafka, outbox, CQRS, Redis locks (Postgres row locks are the concurrency story)
- Shipping statuses, catalog admin, price history
- Real PSP, webhooks, refunds, partial capture
- Customer cancel / line cancel / restock API
- List-all pagination (GET by id is enough for the grade)
- Kubernetes, saga frameworks, extra reservation-hold table (decrement-at-create + restock-on-cancel is the spec)

---

## Config knobs

| Property | Default | Meaning |
|---|---|---|
| `orders.payment-timeout` | `15m` | Unpaid TTL |
| `orders.job.enabled` | `true` | Poller on/off |
| `orders.job.interval-ms` | `5000` | Tick interval |
| `orders.payment.max-attempts` | `3` | Then cancel + restock |

Datasource for local Compose: `jdbc:postgresql://localhost:5432/orders` / `orders` / `orders`.
