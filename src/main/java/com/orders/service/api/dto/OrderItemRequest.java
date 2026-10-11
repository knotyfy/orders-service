package com.orders.service.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * One line on a create-order request.
 *
 * @param sku      catalog SKU
 * @param quantity units to reserve; must be at least 1
 */
public record OrderItemRequest(
        @NotBlank String sku,
        @Min(1) int quantity
) {
}
