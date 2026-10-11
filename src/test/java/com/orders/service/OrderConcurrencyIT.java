package com.orders.service;

import com.orders.service.api.dto.CreateOrderRequest;
import com.orders.service.api.dto.OrderItemRequest;
import com.orders.service.api.dto.OrderResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Grade tests: last-unit race, insufficient stock, invalid quantity, and idempotent duplicate POST.
 */
class OrderConcurrencyIT extends AbstractOrdersIT {

    /** Two parallel qty=1 POSTs against stock=1: one 201, one 409, final stock 0. */
    @Test
    void lastUnitRace_oneWinnerOneConflict() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<ResponseEntity<OrderResponse>> a = pool.submit(() -> {
            start.await();
            return postOrder("cust-a", "WIDGET-1", 1, "key-a");
        });
        Future<ResponseEntity<OrderResponse>> b = pool.submit(() -> {
            start.await();
            return postOrder("cust-b", "WIDGET-1", 1, "key-b");
        });
        start.countDown();
        ResponseEntity<OrderResponse> first = a.get(15, TimeUnit.SECONDS);
        ResponseEntity<OrderResponse> second = b.get(15, TimeUnit.SECONDS);
        pool.shutdownNow();

        List<HttpStatus> statuses = List.of(
                HttpStatus.valueOf(first.getStatusCode().value()),
                HttpStatus.valueOf(second.getStatusCode().value())
        );
        assertThat(statuses).containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.CONFLICT);
        assertThat(stock("WIDGET-1")).isZero();
        assertThat(orderRepository.count()).isEqualTo(1);
    }

    /** Qty above stock returns INSUFFICIENT_STOCK and does not write an order. */
    @Test
    void insufficientStock_leavesCatalogUnchanged() {
        setStock("WIDGET-10", 2);
        ResponseEntity<ProblemDetail> response = postProblem("cust-1", "WIDGET-10", 3, "key-short");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties()).containsEntry("code", "INSUFFICIENT_STOCK");
        assertThat(stock("WIDGET-10")).isEqualTo(2);
        assertThat(orderRepository.count()).isZero();
    }

    /** Quantity 0 is 400 VALIDATION_ERROR and leaves stock unchanged. */
    @Test
    void invalidQuantity_isRejected() {
        CreateOrderRequest zero = new CreateOrderRequest("cust-1", List.of(new OrderItemRequest("WIDGET-10", 0)));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", "key-zero");
        ResponseEntity<ProblemDetail> response = http.postForEntity(
                "/api/v1/orders", new HttpEntity<>(zero, headers), ProblemDetail.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getProperties()).containsEntry("code", "VALIDATION_ERROR");
        assertThat(stock("WIDGET-10")).isEqualTo(10);
        assertThat(orderRepository.count()).isZero();
    }

    /** Same Idempotency-Key reserves once (serial replay and overlapping threads). */
    @Test
    void duplicatePost_reservesOnce() throws Exception {
        String key = "same-key";
        ResponseEntity<OrderResponse> serial = postOrder("cust-1", "WIDGET-10", 1, key);
        assertThat(serial.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID orderId = serial.getBody().id();

        ResponseEntity<OrderResponse> replay = postOrder("cust-1", "WIDGET-10", 1, key);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replay.getBody().id()).isEqualTo(orderId);
        assertThat(stock("WIDGET-10")).isEqualTo(9);

        resetCatalog();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<ResponseEntity<OrderResponse>> a = pool.submit(() -> {
            start.await();
            return postOrder("cust-2", "WIDGET-10", 1, "overlap-key");
        });
        Future<ResponseEntity<OrderResponse>> b = pool.submit(() -> {
            start.await();
            return postOrder("cust-2", "WIDGET-10", 1, "overlap-key");
        });
        start.countDown();
        ResponseEntity<OrderResponse> first = a.get(15, TimeUnit.SECONDS);
        ResponseEntity<OrderResponse> second = b.get(15, TimeUnit.SECONDS);
        pool.shutdownNow();

        Set<HttpStatus> codes = Set.of(
                HttpStatus.valueOf(first.getStatusCode().value()),
                HttpStatus.valueOf(second.getStatusCode().value())
        );
        assertThat(codes).isSubsetOf(HttpStatus.CREATED, HttpStatus.OK);
        assertThat(first.getBody().id()).isEqualTo(second.getBody().id());
        assertThat(stock("WIDGET-10")).isEqualTo(9);
        assertThat(orderRepository.count()).isEqualTo(1);
    }

    /**
     * POSTs an order expecting a ProblemDetail error body.
     *
     * @param customerId customer field
     * @param sku        line SKU
     * @param qty        quantity
     * @param key        Idempotency-Key
     * @return error response
     */
    private ResponseEntity<ProblemDetail> postProblem(String customerId, String sku, int qty, String key) {
        CreateOrderRequest body = new CreateOrderRequest(customerId, List.of(new OrderItemRequest(sku, qty)));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", key);
        return http.postForEntity("/api/v1/orders", new HttpEntity<>(body, headers), ProblemDetail.class);
    }
}
