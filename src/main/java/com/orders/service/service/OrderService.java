package com.orders.service.service;

import com.orders.service.api.dto.CreateOrderRequest;
import com.orders.service.api.dto.CreatedOrder;
import com.orders.service.api.dto.OrderItemRequest;
import com.orders.service.api.dto.OrderResponse;
import com.orders.service.api.dto.ProductResponse;
import com.orders.service.config.OrdersProperties;
import com.orders.service.domain.Order;
import com.orders.service.domain.OrderItem;
import com.orders.service.domain.Product;
import com.orders.service.error.ApiException;
import com.orders.service.error.ErrorCode;
import com.orders.service.repo.OrderRepository;
import com.orders.service.repo.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Create-order and read path: validation, idempotency, stock reservation, and price snapshot.
 */
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final OrdersProperties properties;
    private final Clock clock;

    /**
     * @param orderRepository   order persistence and advisory lock
     * @param productRepository catalog lock and stock updates
     * @param properties        payment timeout for {@code expiresAt}
     * @param clock             create timestamps
     */
    public OrderService(
            OrderRepository orderRepository,
            ProductRepository productRepository,
            OrdersProperties properties,
            Clock clock
    ) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Validates the request, serializes on the idempotency key, then either replays or reserves stock
     * and persists {@code PENDING_PAYMENT}.
     *
     * @param request        create payload
     * @param idempotencyKey required Idempotency-Key header
     * @return created or replayed order
     */
    @Transactional
    public CreatedOrder create(CreateOrderRequest request, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "Idempotency-Key is required");
        }
        validateDistinctSkus(request.items());
        String hash = RequestHash.of(request);
        // Serializes duplicate POSTs so the second call sees the first reservation instead of decrementing twice.
        orderRepository.acquireIdempotencyLock(request.customerId() + ":" + idempotencyKey);

        return orderRepository.findByCustomerIdAndIdempotencyKey(request.customerId(), idempotencyKey)
                .map(existing -> replayOrConflict(existing, hash))
                .orElseGet(() -> persistNew(request, idempotencyKey, hash));
    }

    /**
     * Loads an order by id with line items.
     *
     * @param orderId order identifier
     * @return API representation
     */
    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId) {
        Order order = orderRepository.findDetailedById(orderId)
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND, "Order not found"));
        return OrderResponse.from(order);
    }

    /**
     * Loads current catalog price and stock for a SKU.
     *
     * @param sku product SKU
     * @return API representation
     */
    @Transactional(readOnly = true)
    public ProductResponse getProduct(String sku) {
        Product product = productRepository.findBySku(sku)
                .orElseThrow(() -> new ApiException(ErrorCode.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND, "Product not found"));
        return ProductResponse.from(product);
    }

    /**
     * Locks products, checks stock, snapshots prices, decrements inventory, and saves a new order.
     *
     * @param request        create payload
     * @param idempotencyKey key stored on the order
     * @param hash           canonical body hash
     * @return newly persisted order ({@code replay=false})
     */
    private CreatedOrder persistNew(CreateOrderRequest request, String idempotencyKey, String hash) {
        List<String> skus = request.items().stream().map(OrderItemRequest::sku).sorted().toList();
        List<Product> locked = productRepository.lockBySkusOrderBySku(skus);
        if (locked.size() != skus.size()) {
            throw new ApiException(ErrorCode.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND, "One or more SKUs were not found");
        }
        Map<String, Product> bySku = locked.stream().collect(Collectors.toMap(Product::getSku, Function.identity()));
        Map<String, Integer> qtyBySku = request.items().stream()
                .collect(Collectors.toMap(OrderItemRequest::sku, OrderItemRequest::quantity));

        String currency = locked.getFirst().getCurrency();
        for (Product product : locked) {
            int qty = qtyBySku.get(product.getSku());
            if (product.getStock() < qty) {
                throw new ApiException(
                        ErrorCode.INSUFFICIENT_STOCK,
                        HttpStatus.CONFLICT,
                        "Insufficient stock for SKU " + product.getSku());
            }
            if (!currency.equals(product.getCurrency())) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "Mixed currencies are not supported");
            }
        }

        Instant now = clock.instant();
        BigDecimal total = BigDecimal.ZERO;
        for (Product product : locked) {
            int qty = qtyBySku.get(product.getSku());
            total = total.add(product.getUnitPrice().multiply(BigDecimal.valueOf(qty)));
            product.decrementStock(qty);
        }

        Order order = Order.pendingPayment(
                request.customerId(),
                currency,
                total,
                idempotencyKey,
                hash,
                now,
                now.plus(properties.paymentTimeout())
        );
        for (Product product : locked) {
            order.addItem(OrderItem.snapshot(product, qtyBySku.get(product.getSku())));
        }
        return new CreatedOrder(OrderResponse.from(orderRepository.save(order)), false);
    }

    /**
     * Returns the existing order if the body hash matches; otherwise raises {@code IDEMPOTENCY_CONFLICT}.
     *
     * @param existing order already stored for this key
     * @param hash     hash of the current request
     * @return replay result
     */
    private static CreatedOrder replayOrConflict(Order existing, String hash) {
        if (!existing.getRequestHash().equals(hash)) {
            throw new ApiException(
                    ErrorCode.IDEMPOTENCY_CONFLICT,
                    HttpStatus.CONFLICT,
                    "Idempotency-Key was reused with a different body");
        }
        return new CreatedOrder(OrderResponse.from(existing), true);
    }

    /**
     * Rejects duplicate SKUs in one payload (quantities are not merged).
     *
     * @param items request lines
     */
    private static void validateDistinctSkus(List<OrderItemRequest> items) {
        Set<String> seen = new HashSet<>();
        for (OrderItemRequest item : items) {
            if (!seen.add(item.sku())) {
                throw new ApiException(
                        ErrorCode.VALIDATION_ERROR,
                        HttpStatus.BAD_REQUEST,
                        "Duplicate SKU in request: " + item.sku());
            }
        }
    }
}
