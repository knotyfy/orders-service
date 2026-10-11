package com.orders.service.api.dto;

/**
 * Result of create: the order plus whether this call was an idempotent replay.
 *
 * @param order  persisted or replayed order
 * @param replay true when the same Idempotency-Key and body were seen before (HTTP 200)
 */
public record CreatedOrder(OrderResponse order, boolean replay) {
}
