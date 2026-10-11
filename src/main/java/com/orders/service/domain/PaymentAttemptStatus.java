package com.orders.service.domain;

/**
 * Status of one gateway attempt row.
 */
public enum PaymentAttemptStatus {
    /** Claim succeeded; charge not yet recorded. */
    CLAIMED,
    /** Gateway returned SUCCESS. */
    SUCCESS,
    /** Gateway returned FAILURE. */
    FAILURE
}
