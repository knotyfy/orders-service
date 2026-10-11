package com.orders.service;

import com.orders.service.api.dto.OrderResponse;
import com.orders.service.domain.CancelReason;
import com.orders.service.domain.OrderStatus;
import com.orders.service.job.OrderFulfillmentJob;
import com.orders.service.service.OrderFulfillmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Payment poller tests: overlapping claims charge once; exhausted FAIL-* attempts cancel and restock.
 */
class PaymentJobIT extends AbstractOrdersIT {

    @Autowired
    OrderFulfillmentJob job;

    @Autowired
    OrderFulfillmentService fulfillmentService;

    /** Two concurrent chargeIfClaimed calls invoke the gateway exactly once and leave the order PAID. */
    @Test
    void overlappingClaims_chargeOnce() throws Exception {
        ResponseEntity<OrderResponse> created = postOrder("cust-pay", "WIDGET-10", 1, "pay-once");
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID orderId = created.getBody().id();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        pool.submit(() -> {
            start.await();
            fulfillmentService.chargeIfClaimed(orderId);
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
        assertThat(order.status()).isEqualTo(OrderStatus.PAID);
        assertThat(paymentGateway.chargeCount()).isEqualTo(1);
        assertThat(stock("WIDGET-10")).isEqualTo(9);
    }

    /** FAIL-CARD fails three times then CANCELLED / PAYMENT_FAILED with stock restored. */
    @Test
    void exhaustedAttempts_cancelAndRestock() {
        ResponseEntity<OrderResponse> created = postOrder("cust-fail", "FAIL-CARD", 2, "fail-key");
        UUID orderId = created.getBody().id();
        assertThat(stock("FAIL-CARD")).isEqualTo(8);

        job.run();
        job.run();
        job.run();
        job.run();

        OrderResponse order = getOrder(orderId);
        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.cancelledReason()).isEqualTo(CancelReason.PAYMENT_FAILED);
        assertThat(order.paymentAttempts()).isEqualTo(3);
        assertThat(stock("FAIL-CARD")).isEqualTo(10);
        assertThat(paymentGateway.chargeCount()).isEqualTo(3);
    }
}
