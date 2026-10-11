package com.orders.service;

import com.orders.service.api.dto.OrderResponse;
import com.orders.service.domain.CancelReason;
import com.orders.service.domain.OrderStatus;
import com.orders.service.job.OrderFulfillmentJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Expiry tests: unpaid orders cancel and restock; PAID orders never expire.
 */
class OrderExpiryIT extends AbstractOrdersIT {

    @Autowired
    OrderFulfillmentJob job;

    @Autowired
    JdbcTemplate jdbc;

    /** Backdated PENDING_PAYMENT is cancelled as EXPIRED and catalog stock is restored. */
    @Test
    void unpaidOrderExpiresAndRestocks() {
        var created = postOrder("cust-exp", "WIDGET-10", 1, "exp-1");
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID orderId = created.getBody().id();
        assertThat(stock("WIDGET-10")).isEqualTo(9);

        jdbc.update("UPDATE orders SET expires_at = now() - interval '1 hour' WHERE id = ?", orderId);
        job.run();

        OrderResponse order = getOrder(orderId);
        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.cancelledReason()).isEqualTo(CancelReason.EXPIRED);
        assertThat(stock("WIDGET-10")).isEqualTo(10);
        assertThat(paymentGateway.chargeCount()).isZero();
    }

    /** After PAID, moving expires_at into the past does not cancel or restock. */
    @Test
    void paidOrderNeverExpires() {
        var created = postOrder("cust-paid", "WIDGET-10", 1, "paid-1");
        UUID orderId = created.getBody().id();

        job.run();
        OrderResponse paid = getOrder(orderId);
        assertThat(paid.status()).isEqualTo(OrderStatus.PAID);
        int heldStock = stock("WIDGET-10");

        jdbc.update("UPDATE orders SET expires_at = now() - interval '1 hour' WHERE id = ?", orderId);
        job.run();

        OrderResponse stillPaid = getOrder(orderId);
        assertThat(stillPaid.status()).isEqualTo(OrderStatus.PAID);
        assertThat(stock("WIDGET-10")).isEqualTo(heldStock);
    }
}
