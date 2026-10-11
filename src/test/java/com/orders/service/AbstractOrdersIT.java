package com.orders.service;

import com.orders.service.api.dto.CreateOrderRequest;
import com.orders.service.api.dto.OrderItemRequest;
import com.orders.service.api.dto.OrderResponse;
import com.orders.service.api.dto.ProductResponse;
import com.orders.service.domain.Product;
import com.orders.service.payment.MockPaymentGateway;
import com.orders.service.repo.OrderRepository;
import com.orders.service.repo.PaymentAttemptRepository;
import com.orders.service.repo.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

/**
 * Shared Testcontainers Postgres + HTTP helpers for order integration tests.
 * Resets seed catalog stock and deletes orders before each test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public abstract class AbstractOrdersIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("orders")
            .withUsername("orders")
            .withPassword("orders");

    /**
     * Points Spring at the Testcontainers JDBC URL.
     *
     * @param registry Spring dynamic properties
     */
    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected TestRestTemplate http;

    @Autowired
    protected ProductRepository productRepository;

    @Autowired
    protected OrderRepository orderRepository;

    @Autowired
    protected PaymentAttemptRepository paymentAttemptRepository;

    @Autowired
    protected MockPaymentGateway paymentGateway;

    /** Clears orders and restores WIDGET-1 / WIDGET-10 / FAIL-CARD seed stock. */
    @BeforeEach
    void resetCatalog() {
        paymentAttemptRepository.deleteAll();
        orderRepository.deleteAll();
        setStock("WIDGET-1", 1);
        setStock("WIDGET-10", 10);
        setStock("FAIL-CARD", 10);
        paymentGateway.resetChargeCount();
    }

    /**
     * Overwrites on-hand stock for a seed SKU.
     *
     * @param sku   catalog SKU
     * @param stock new quantity
     */
    protected void setStock(String sku, int stock) {
        Product product = productRepository.findBySku(sku).orElseThrow();
        product.setStock(stock);
        productRepository.saveAndFlush(product);
    }

    /**
     * Reads live stock via GET /products/{sku}.
     *
     * @param sku catalog SKU
     * @return current stock
     */
    protected int stock(String sku) {
        return http.getForObject("/api/v1/products/" + sku, ProductResponse.class).stock();
    }

    /**
     * POSTs a single-line order.
     *
     * @param customerId     customer field
     * @param sku            line SKU
     * @param qty            quantity
     * @param idempotencyKey Idempotency-Key header
     * @return HTTP response
     */
    protected ResponseEntity<OrderResponse> postOrder(String customerId, String sku, int qty, String idempotencyKey) {
        CreateOrderRequest body = new CreateOrderRequest(customerId, List.of(new OrderItemRequest(sku, qty)));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);
        return http.postForEntity("/api/v1/orders", new HttpEntity<>(body, headers), OrderResponse.class);
    }

    /**
     * GETs an order by id.
     *
     * @param id order id
     * @return body
     */
    protected OrderResponse getOrder(UUID id) {
        return http.getForObject("/api/v1/orders/" + id, OrderResponse.class);
    }
}
