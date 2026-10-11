package com.orders.service.job;

import com.orders.service.config.OrdersProperties;
import com.orders.service.domain.OrderStatus;
import com.orders.service.repo.OrderRepository;
import com.orders.service.service.OrderFulfillmentService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Single poller: expire unpaid orders first, then claim/charge remaining {@code PENDING_PAYMENT} orders.
 * Tests disable scheduling ({@code orders.job.enabled=false}) and call {@link #run()} directly.
 */
@Component
@ConditionalOnProperty(prefix = "orders.job", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OrderFulfillmentJob {

    private final OrderRepository orderRepository;
    private final OrderFulfillmentService fulfillmentService;
    private final OrdersProperties properties;
    private final Clock clock;

    /**
     * @param orderRepository     finds expired and payable order ids
     * @param fulfillmentService  performs expire and claim/charge in their own transactions
     * @param properties          max attempts and stale-claim window
     * @param clock               current time for due queries
     */
    public OrderFulfillmentJob(
            OrderRepository orderRepository,
            OrderFulfillmentService fulfillmentService,
            OrdersProperties properties,
            Clock clock
    ) {
        this.orderRepository = orderRepository;
        this.fulfillmentService = fulfillmentService;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * One tick: expire due unpaid orders, then charge unexpired pending orders that still have attempts left.
     */
    @Scheduled(fixedDelayString = "${orders.job.interval-ms:5000}")
    public void run() {
        Instant now = clock.instant();
        for (UUID id : orderRepository.findExpiredIds(OrderStatus.PENDING_PAYMENT, now)) {
            fulfillmentService.expireIfDue(id);
        }
        Instant staleBefore = now.minus(properties.job().staleClaim());
        for (UUID id : orderRepository.findPayableIds(now, properties.payment().maxAttempts(), staleBefore)) {
            fulfillmentService.chargeIfClaimed(id);
        }
    }
}
