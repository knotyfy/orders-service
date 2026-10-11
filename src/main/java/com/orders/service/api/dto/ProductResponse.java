package com.orders.service.api.dto;

import com.orders.service.domain.Product;

import java.math.BigDecimal;

/**
 * JSON representation of a catalog product including current stock.
 *
 * @param sku       unique SKU
 * @param name      display name
 * @param unitPrice current catalog price
 * @param currency  ISO currency
 * @param stock     units remaining after reservations and restocks
 */
public record ProductResponse(
        String sku,
        String name,
        BigDecimal unitPrice,
        String currency,
        int stock
) {
    /**
     * Maps a product entity to the API shape.
     *
     * @param product catalog row
     * @return response DTO
     */
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getSku(),
                product.getName(),
                product.getUnitPrice(),
                product.getCurrency(),
                product.getStock()
        );
    }
}
