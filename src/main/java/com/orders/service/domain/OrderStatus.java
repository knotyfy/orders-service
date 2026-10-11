package com.orders.service.domain;

/**
 * Lifecycle of an order. {@code PAID} and {@code CANCELLED} are terminal.
 */
public enum OrderStatus {
    /** Stock reserved; awaiting mock payment or timeout. */
    PENDING_PAYMENT,
    /** Payment succeeded; stock stays decremented. */
    PAID,
    /** Payment failed or unpaid timeout; stock restored once. */
    CANCELLED
}
