package com.orders.service.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Order aggregate: customer, status, money snapshot, idempotency, payment claim, and line items.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Column(name = "payment_attempts", nullable = false)
    private int paymentAttempts;

    @Column(name = "payment_claimed_at")
    private Instant paymentClaimedAt;

    @Column(name = "payment_claimed_by")
    private String paymentClaimedBy;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancelled_reason")
    private CancelReason cancelledReason;

    @Column(nullable = false)
    private boolean restocked;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    /** JPA constructor. */
    protected Order() {
    }

    /**
     * Factory for a new unpaid order after stock has already been decremented.
     *
     * @param customerId     customer on the request
     * @param currency       catalog currency
     * @param totalAmount    sum of snapshot line totals
     * @param idempotencyKey create header
     * @param requestHash    canonical body hash
     * @param now            create time
     * @param expiresAt      unpaid timeout
     * @return PENDING_PAYMENT order without items yet
     */
    public static Order pendingPayment(
            String customerId,
            String currency,
            BigDecimal totalAmount,
            String idempotencyKey,
            String requestHash,
            Instant now,
            Instant expiresAt
    ) {
        Order order = new Order();
        order.id = UUID.randomUUID();
        order.customerId = customerId;
        order.status = OrderStatus.PENDING_PAYMENT;
        order.currency = currency;
        order.totalAmount = totalAmount;
        order.idempotencyKey = idempotencyKey;
        order.requestHash = requestHash;
        order.paymentAttempts = 0;
        order.expiresAt = expiresAt;
        order.restocked = false;
        order.createdAt = now;
        order.updatedAt = now;
        return order;
    }

    /**
     * Attaches a line item and sets the back-reference.
     *
     * @param item snapshot line
     */
    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    /**
     * Marks the order PAID and clears any payment claim.
     *
     * @param now update time
     */
    public void markPaid(Instant now) {
        this.status = OrderStatus.PAID;
        this.paymentClaimedAt = null;
        this.paymentClaimedBy = null;
        this.updatedAt = now;
    }

    /**
     * Marks the order CANCELLED with a reason and clears any payment claim.
     *
     * @param reason PAYMENT_FAILED or EXPIRED
     * @param now    update time
     */
    public void markCancelled(CancelReason reason, Instant now) {
        this.status = OrderStatus.CANCELLED;
        this.cancelledReason = reason;
        this.paymentClaimedAt = null;
        this.paymentClaimedBy = null;
        this.updatedAt = now;
    }

    /**
     * Increments failed-attempt count and releases the claim so the poller can retry.
     *
     * @param now update time
     */
    public void recordFailedAttempt(Instant now) {
        this.paymentAttempts += 1;
        this.paymentClaimedAt = null;
        this.paymentClaimedBy = null;
        this.updatedAt = now;
    }

    /**
     * Flip {@code restocked} from false to true. Returns false if stock was already restored.
     *
     * @return true if this caller should increment product stock
     */
    public boolean markRestocked() {
        if (restocked) {
            return false;
        }
        restocked = true;
        return true;
    }

    /** @return order id */
    public UUID getId() {
        return id;
    }

    /** @return customer id */
    public String getCustomerId() {
        return customerId;
    }

    /** @return current status */
    public OrderStatus getStatus() {
        return status;
    }

    /** @return ISO currency */
    public String getCurrency() {
        return currency;
    }

    /** @return snapshot total */
    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    /** @return stored Idempotency-Key */
    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    /** @return canonical request hash */
    public String getRequestHash() {
        return requestHash;
    }

    /** @return completed payment attempts */
    public int getPaymentAttempts() {
        return paymentAttempts;
    }

    /** @return unpaid timeout */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    /** @return when the poller claimed this order, or null */
    public Instant getPaymentClaimedAt() {
        return paymentClaimedAt;
    }

    /** @return cancel reason, or null if not cancelled */
    public CancelReason getCancelledReason() {
        return cancelledReason;
    }

    /** @return true if catalog stock was restored */
    public boolean isRestocked() {
        return restocked;
    }

    /** @return create timestamp */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /** @return last update timestamp */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** @return line items */
    public List<OrderItem> getItems() {
        return items;
    }
}
