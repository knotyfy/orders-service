package com.orders.service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Binds {@code orders.*} settings: unpaid timeout, payment retry limit, and poller behaviour.
 *
 * @param paymentTimeout how long a new order stays {@code PENDING_PAYMENT} before expiry
 * @param payment        mock-gateway retry settings
 * @param job            poller enablement, tick interval, and stale-claim TTL
 */
@ConfigurationProperties(prefix = "orders")
public record OrdersProperties(
        @DefaultValue("15m") Duration paymentTimeout,
        Payment payment,
        Job job
) {
    /**
     * Payment-attempt limits for the mock gateway.
     *
     * @param maxAttempts failures allowed before the order is cancelled and restocked
     */
    public record Payment(@DefaultValue("3") int maxAttempts) {
    }

    /**
     * Background poller settings.
     *
     * @param enabled     when false, Spring does not schedule the job (tests call it manually)
     * @param intervalMs  delay between poller ticks
     * @param staleClaim  age after which a crashed worker's payment claim may be reused
     */
    public record Job(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("5000") long intervalMs,
            @DefaultValue("2m") Duration staleClaim
    ) {
    }
}
