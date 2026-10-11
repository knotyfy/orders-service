package com.orders.service;

import com.orders.service.api.dto.OrderResponse;
import com.orders.service.domain.OrderStatus;
import com.orders.service.service.OrderFulfillmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Polish race: expire vs claim on the same unpaid order ends in exactly one terminal status.
 */
class JobVsApiRaceIT extends AbstractOrdersIT {

    @Autowired
    OrderFulfillmentService fulfillmentService;

    @Autowired
    JdbcTemplate jdbc;

    /** Parallel expireIfDue and chargeIfClaimed: PAID without restock, or CANCELLED with restock — never both. */
    @Test
    void expireVersusClaim_singleTerminalState() throws Exception {
        var created = postOrder("cust-race", "WIDGET-10", 1, "race-key");
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID orderId = created.getBody().id();
        jdbc.update("UPDATE orders SET expires_at = now() - interval '1 hour' WHERE id = ?", orderId);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        pool.submit(() -> {
            start.await();
            fulfillmentService.expireIfDue(orderId);
            return null;
        });
        pool.submit(() -> {
            start.await();
            fulfillmentService.chargeIfClaimed(orderId);
            return null;
        });
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue();

        OrderResponse order = getOrder(orderId);
        assertThat(order.status()).isIn(OrderStatus.PAID, OrderStatus.CANCELLED);

        if (order.status() == OrderStatus.PAID) {
            assertThat(stock("WIDGET-10")).isEqualTo(9);
            assertThat(orderRepository.findDetailedById(orderId).orElseThrow().isRestocked()).isFalse();
            assertThat(paymentGateway.chargeCount()).isEqualTo(1);
        } else {
            assertThat(stock("WIDGET-10")).isEqualTo(10);
            assertThat(orderRepository.findDetailedById(orderId).orElseThrow().isRestocked()).isTrue();
        }
    }
}
