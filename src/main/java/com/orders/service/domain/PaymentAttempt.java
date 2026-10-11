package com.orders.service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One mock-gateway attempt. Unique {@code (orderId, attemptNumber)} stops two workers from charging the same try.
 */
@Entity
@Table(name = "payment_attempts")
public class PaymentAttempt {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentAttemptStatus status;

    @Column(name = "claimed_at", nullable = false)
    private Instant claimedAt;

    /** JPA constructor. */
    protected PaymentAttempt() {
    }

    /**
     * Opens a CLAIMED attempt after the order row claim succeeds, before {@code charge()} is called.
     *
     * @param orderId        order being charged
     * @param attemptNumber  1-based attempt
     * @param claimedAt      claim time
     * @return new attempt row
     */
    public static PaymentAttempt claimed(UUID orderId, int attemptNumber, Instant claimedAt) {
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.id = UUID.randomUUID();
        attempt.orderId = orderId;
        attempt.attemptNumber = attemptNumber;
        attempt.status = PaymentAttemptStatus.CLAIMED;
        attempt.claimedAt = claimedAt;
        return attempt;
    }

    /** Records a successful gateway response. */
    public void markSuccess() {
        this.status = PaymentAttemptStatus.SUCCESS;
    }

    /** Records a declined gateway response. */
    public void markFailure() {
        this.status = PaymentAttemptStatus.FAILURE;
    }

    /** @return attempt id */
    public UUID getId() {
        return id;
    }

    /** @return 1-based attempt number */
    public int getAttemptNumber() {
        return attemptNumber;
    }

    /** @return CLAIMED, SUCCESS, or FAILURE */
    public PaymentAttemptStatus getStatus() {
        return status;
    }
}
