package com.orders.service.payment;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Input to {@link PaymentGateway#charge(PaymentRequest)}.
 *
 * @param orderId        order being charged
 * @param customerId     used by the mock to force failure ({@code customer-fail})
 * @param amount         order total
 * @param attemptNumber  1-based attempt
 * @param skus           line SKUs; {@code FAIL-*} forces mock failure
 */
public record PaymentRequest(
        UUID orderId,
        String customerId,
        BigDecimal amount,
        int attemptNumber,
        List<String> skus
) {
}
