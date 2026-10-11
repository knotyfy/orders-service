package com.orders.service.repo;

import com.orders.service.domain.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Persistence for payment attempt rows. Unique {@code (order_id, attempt_number)} prevents double-charge.
 */
public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {
}
