package com.orders.service.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Body for {@code POST /api/v1/orders}. Prices are not accepted; the catalog is the source of truth.
 *
 * @param customerId customer identifier (no auth; treated as a required field)
 * @param items      one or more SKU/quantity lines; duplicate SKUs are rejected
 */
public record CreateOrderRequest(
        @NotBlank String customerId,
        @NotEmpty @Valid List<OrderItemRequest> items
) {
}
