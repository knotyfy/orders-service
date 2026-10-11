package com.orders.service.service;

import com.orders.service.api.dto.CreateOrderRequest;
import com.orders.service.api.dto.OrderItemRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * Canonical SHA-256 of a create-order body, used to detect Idempotency-Key reuse with a different payload.
 */
final class RequestHash {

    /**
     * Not instantiable; use {@link #of(CreateOrderRequest)}.
     */
    private RequestHash() {
    }

    /**
     * Hashes {@code customerId} plus SKU:qty lines sorted by SKU.
     *
     * @param request create payload
     * @return lowercase hex SHA-256
     */
    static String of(CreateOrderRequest request) {
        StringBuilder canonical = new StringBuilder(request.customerId()).append('\n');
        List<OrderItemRequest> sorted = request.items().stream()
                .sorted(Comparator.comparing(OrderItemRequest::sku))
                .toList();
        for (OrderItemRequest item : sorted) {
            canonical.append(item.sku()).append(':').append(item.quantity()).append('\n');
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
