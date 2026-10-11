package com.orders.service.payment;

/**
 * Outcome of a mock (or real) payment charge.
 */
public enum PaymentResult {
    /** Funds captured; order may move to PAID. */
    SUCCESS,
    /** Charge declined; poller retries until max attempts then cancels. */
    FAILURE
}
