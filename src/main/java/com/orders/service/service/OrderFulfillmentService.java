package com.orders.service.service;

import com.orders.service.config.OrdersProperties;
import com.orders.service.domain.CancelReason;
import com.orders.service.domain.Order;
import com.orders.service.domain.OrderItem;
import com.orders.service.domain.OrderStatus;
import com.orders.service.domain.PaymentAttempt;
import com.orders.service.domain.Product;
import com.orders.service.payment.PaymentGateway;
import com.orders.service.payment.PaymentRequest;
import com.orders.service.payment.PaymentResult;
import com.orders.service.repo.OrderRepository;
import com.orders.service.repo.PaymentAttemptRepository;
import com.orders.service.repo.ProductRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Poller-side mutations: expire unpaid orders and claim/charge pending ones without double-pay or double-restock.
 */
@Service
public class OrderFulfillmentService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentGateway paymentGateway;
    private final OrdersProperties properties;
    private final Clock clock;

    /**
     * @param orderRepository            conditional expire/claim updates
     * @param productRepository          stock restore under row locks
     * @param paymentAttemptRepository   attempt uniqueness backstop
     * @param paymentGateway             mock (or real) charger
     * @param properties                 max attempts and stale-claim window
     * @param clock                      current time
     */
    public OrderFulfillmentService(
            OrderRepository orderRepository,
            ProductRepository productRepository,
            PaymentAttemptRepository paymentAttemptRepository,
            PaymentGateway paymentGateway,
            OrdersProperties properties,
            Clock clock
    ) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.paymentGateway = paymentGateway;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Cancels an unpaid order that is past {@code expiresAt} and not claimed for payment, then restocks once.
     *
     * @param orderId order to consider
     */
    @Transactional
    public void expireIfDue(UUID orderId) {
        Instant now = clock.instant();
        // Conditional update: loses to an in-flight payment claim, never restocks a PAID order.
        int expired = orderRepository.expireUnclaimed(orderId, now);
        if (expired == 0) {
            return;
        }
        Order order = orderRepository.findDetailedById(orderId).orElse(null);
        if (order != null) {
            restock(order);
        }
    }

    /**
     * Claims the order, calls the gateway once, then marks PAID or records a failure.
     * A 0-row claim means another worker or expiry already owns this order.
     *
     * @param orderId order to charge
     */
    @Transactional
    public void chargeIfClaimed(UUID orderId) {
        Instant now = clock.instant();
        Instant staleBefore = now.minus(properties.job().staleClaim());
        int claimed = orderRepository.claimForPayment(orderId, now, workerId(), staleBefore);
        if (claimed == 0) {
            return;
        }

        Order order = orderRepository.findDetailedById(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return;
        }

        int attemptNumber = order.getPaymentAttempts() + 1;
        PaymentAttempt attempt = PaymentAttempt.claimed(orderId, attemptNumber, now);
        try {
            paymentAttemptRepository.saveAndFlush(attempt);
        } catch (DataIntegrityViolationException ignored) {
            return;
        }

        PaymentResult result = paymentGateway.charge(new PaymentRequest(
                order.getId(),
                order.getCustomerId(),
                order.getTotalAmount(),
                attemptNumber,
                order.getItems().stream().map(OrderItem::getSku).toList()
        ));

        if (result == PaymentResult.SUCCESS) {
            attempt.markSuccess();
            order.markPaid(now);
            return;
        }

        attempt.markFailure();
        order.recordFailedAttempt(now);
        if (order.getPaymentAttempts() >= properties.payment().maxAttempts()) {
            order.markCancelled(CancelReason.PAYMENT_FAILED, now);
            restock(order);
        }
    }

    /**
     * Returns reserved units to the catalog exactly once ({@code restocked} flag).
     *
     * @param order cancelled order whose stock must be restored
     */
    private void restock(Order order) {
        if (!order.markRestocked()) {
            return;
        }
        List<String> skus = order.getItems().stream().map(OrderItem::getSku).sorted().toList();
        Map<String, Product> products = productRepository.lockBySkusOrderBySku(skus).stream()
                .collect(Collectors.toMap(Product::getSku, Function.identity()));
        for (OrderItem item : order.getItems()) {
            Product product = products.get(item.getSku());
            if (product != null) {
                product.incrementStock(item.getQuantity());
            }
        }
    }

    /**
     * @return identifier stored on {@code payment_claimed_by} for this poller thread
     */
    private static String workerId() {
        return "poller-" + Thread.currentThread().threadId();
    }
}
