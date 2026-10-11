package com.orders.service.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Snapshot of a line item as stored on the order (not live catalog data).
 *
 * @param productId   catalog product id at create time
 * @param sku         SKU snapshot
 * @param productName name snapshot
 * @param quantity    reserved quantity
 * @param unitPrice   catalog unit price at create time
 */
public record OrderItemResponse(
        UUID productId,
        String sku,
        String productName,
        int quantity,
        BigDecimal unitPrice
) {
}
