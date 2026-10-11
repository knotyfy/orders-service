/**
 * JPA model: Product, Order, OrderItem, PaymentAttempt.
 *
 * <p>Statuses: {@code PENDING_PAYMENT}, {@code PAID}, {@code CANCELLED}.
 * Cancel reasons: {@code PAYMENT_FAILED}, {@code EXPIRED}. Money is snapshot
 * onto line items at create; catalog price is not re-read after that.
 */
package com.orders.service.domain;
