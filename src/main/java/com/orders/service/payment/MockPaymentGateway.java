package com.orders.service.payment;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-process payment mock. Declines {@code customer-fail} and SKUs prefixed {@code FAIL-};
 * otherwise succeeds. Counts {@link #charge} calls so tests can prove the job does not double-pay.
 */
@Component
public class MockPaymentGateway implements PaymentGateway {

    /** Customer id that always declines. */
    public static final String FAIL_CUSTOMER = "customer-fail";
    /** SKU prefix that always declines. */
    public static final String FAIL_SKU_PREFIX = "FAIL-";

    private final AtomicInteger chargeCount = new AtomicInteger();

    /**
     * Records one charge and returns SUCCESS or FAILURE per the deterministic rules above.
     *
     * @param request charge input
     * @return mock result
     */
    @Override
    public PaymentResult charge(PaymentRequest request) {
        chargeCount.incrementAndGet();
        if (FAIL_CUSTOMER.equals(request.customerId())) {
            return PaymentResult.FAILURE;
        }
        boolean failSku = request.skus().stream().anyMatch(sku -> sku.startsWith(FAIL_SKU_PREFIX));
        return failSku ? PaymentResult.FAILURE : PaymentResult.SUCCESS;
    }

    /**
     * @return how many times {@link #charge} has been invoked since last reset
     */
    public int chargeCount() {
        return chargeCount.get();
    }

    /**
     * Clears the charge counter (used between integration tests).
     */
    public void resetChargeCount() {
        chargeCount.set(0);
    }
}
