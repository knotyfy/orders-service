package com.orders.service.api.dto;

import com.orders.service.domain.CancelReason;
import com.orders.service.domain.Order;
import com.orders.service.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * JSON representation of an order returned by create and get.
 *
 * @param id               order identifier
 * @param customerId       customer who placed the order
 * @param status           PENDING_PAYMENT, PAID, or CANCELLED
 * @param currency         ISO currency from the catalog
 * @param totalAmount      sum of snapshot unit prices × quantities
 * @param paymentAttempts  completed mock-gateway attempts so far
 * @param expiresAt        unpaid timeout; after this the poller may expire the order
 * @param cancelledReason  PAYMENT_FAILED or EXPIRED when cancelled; otherwise null
 * @param createdAt        create timestamp (UTC)
 * @param items            line-item snapshots
 */
public record OrderResponse(
        UUID id,
        String customerId,
        OrderStatus status,
        String currency,
        BigDecimal totalAmount,
        int paymentAttempts,
        Instant expiresAt,
        CancelReason cancelledReason,
        Instant createdAt,
        List<OrderItemResponse> items
) {
    /**
     * Maps a persisted order (with items loaded) to the API shape.
     *
     * @param order aggregate root
     * @return response DTO
     */
    public static OrderResponse from(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(i -> new OrderItemResponse(
                        i.getProductId(), i.getSku(), i.getProductName(), i.getQuantity(), i.getUnitPrice()))
                .toList();
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                order.getCurrency(),
                order.getTotalAmount(),
                order.getPaymentAttempts(),
                order.getExpiresAt(),
                order.getCancelledReason(),
                order.getCreatedAt(),
                items
        );
    }
}
