package com.orders.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot entry point for the orders service.
 * Enables scheduling for the payment/expiry poller and scans {@code orders.*} configuration properties.
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class OrdersServiceApplication {

    /**
     * Boots the application context and embedded web server.
     *
     * @param args command-line arguments passed through to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(OrdersServiceApplication.class, args);
    }
}
