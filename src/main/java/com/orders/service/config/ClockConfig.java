package com.orders.service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Supplies the clock used for order timestamps, payment timeout, and expiry checks.
 * Tests may replace this bean to freeze or advance time.
 */
@Configuration
public class ClockConfig {

    /**
     * UTC clock so createdAt, expiresAt, and the poller share one timezone.
     *
     * @return system UTC clock
     */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
