package com.orders.service.api;

import com.orders.service.api.dto.CreateOrderRequest;
import com.orders.service.api.dto.CreatedOrder;
import com.orders.service.api.dto.OrderResponse;
import com.orders.service.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * HTTP API for creating orders and fetching them by id.
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    /**
     * @param orderService application service that owns create/get rules
     */
    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Places an order: validates, reserves stock, snapshots prices, persists {@code PENDING_PAYMENT}.
     * A repeated {@code Idempotency-Key} with the same body returns 200 and the original order.
     *
     * @param request        customer and line items (SKU + quantity only)
     * @param idempotencyKey required header that de-duplicates create
     * @return 201 with Location on first create, 200 on idempotent replay
     */
    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        CreatedOrder created = orderService.create(request, idempotencyKey);
        if (created.replay()) {
            return ResponseEntity.ok(created.order());
        }
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.order().id())
                .toUri();
        return ResponseEntity.created(location).body(created.order());
    }

    /**
     * Returns order details including line-item snapshots, status, and payment timeout.
     *
     * @param orderId order identifier from create
     * @return the order, or 404 if unknown
     */
    @GetMapping("/{orderId}")
    public OrderResponse get(@PathVariable UUID orderId) {
        return orderService.getOrder(orderId);
    }
}
