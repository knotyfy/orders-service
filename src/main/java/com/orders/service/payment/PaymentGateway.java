package com.orders.service.payment;

/**
 * Charges an order. Production would call a PSP; this assignment uses {@link MockPaymentGateway}.
 */
public interface PaymentGateway {

    /**
     * Attempts to collect {@code request.amount()} for the given order.
     *
     * @param request order, customer, amount, attempt number, and SKUs
     * @return SUCCESS or FAILURE
     */
    PaymentResult charge(PaymentRequest request);
}
