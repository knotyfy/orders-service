package com.orders.service.domain;

/**
 * Why a cancelled order left {@code PENDING_PAYMENT}.
 */
public enum CancelReason {
    /** Mock gateway failed {@code max-attempts} times. */
    PAYMENT_FAILED,
    /** Unpaid past {@code expiresAt} with no live payment claim. */
    EXPIRED
}
