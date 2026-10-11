package com.orders.service.error;

/**
 * Stable {@code code} values placed on ProblemDetail responses.
 */
public enum ErrorCode {
    /** Request body or header failed validation. */
    VALIDATION_ERROR,
    /** SKU is not in the catalog. */
    PRODUCT_NOT_FOUND,
    /** Order id does not exist. */
    ORDER_NOT_FOUND,
    /** Requested quantity exceeds current stock. */
    INSUFFICIENT_STOCK,
    /** Same Idempotency-Key was reused with a different body. */
    IDEMPOTENCY_CONFLICT,
    /** Optimistic/claim update lost a race (reserved for concurrent updates). */
    CONCURRENT_UPDATE
}
