package com.orders.service.repo;

import com.orders.service.domain.Order;
import com.orders.service.domain.OrderStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence and conditional updates for orders: idempotency lookup, expiry, and payment claim.
 */
public interface OrderRepository extends JpaRepository<Order, UUID> {

    /**
     * Finds an existing order for the customer + idempotency key, with line items loaded.
     *
     * @param customerId     customer on the request
     * @param idempotencyKey Idempotency-Key header
     * @return matching order if this create was already accepted
     */
    @EntityGraph(attributePaths = "items")
    Optional<Order> findByCustomerIdAndIdempotencyKey(String customerId, String idempotencyKey);

    /**
     * Loads an order and its items by id.
     *
     * @param id order id
     * @return order with items, if present
     */
    @EntityGraph(attributePaths = "items")
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findDetailedById(@Param("id") UUID id);

    /**
     * Takes a transaction-scoped Postgres advisory lock so duplicate POSTs serialize on the same key.
     *
     * @param lockKey typically {@code customerId:idempotencyKey}
     */
    @Query(value = "SELECT pg_advisory_xact_lock(hashtextextended(:lockKey, 0))", nativeQuery = true)
    void acquireIdempotencyLock(@Param("lockKey") String lockKey);

    /**
     * Ids of unclaimed pending orders whose payment timeout has passed.
     *
     * @param status expected {@link OrderStatus#PENDING_PAYMENT}
     * @param now    cutoff time
     * @return expired order ids
     */
    @Query("""
            SELECT o.id FROM Order o
            WHERE o.status = :status
              AND o.expiresAt <= :now
              AND o.paymentClaimedAt IS NULL
            ORDER BY o.expiresAt
            """)
    List<UUID> findExpiredIds(@Param("status") OrderStatus status, @Param("now") Instant now);

    /**
     * Conditionally cancels an unpaid, unclaimed, expired order. Returns 0 if a payment claim already owns it.
     *
     * @param id  order id
     * @param now current time
     * @return number of rows updated (0 or 1)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Order o
            SET o.status = com.orders.service.domain.OrderStatus.CANCELLED,
                o.cancelledReason = com.orders.service.domain.CancelReason.EXPIRED,
                o.updatedAt = :now,
                o.paymentClaimedAt = NULL,
                o.paymentClaimedBy = NULL
            WHERE o.id = :id
              AND o.status = com.orders.service.domain.OrderStatus.PENDING_PAYMENT
              AND o.paymentClaimedAt IS NULL
              AND o.expiresAt <= :now
            """)
    int expireUnclaimed(@Param("id") UUID id, @Param("now") Instant now);

    /**
     * Claims a pending order so only one poller worker calls the gateway.
     * Stale claims (worker crash) become reclaimable after {@code staleBefore}.
     *
     * @param id          order id
     * @param now         claim timestamp
     * @param worker      worker identity stored on the row
     * @param staleBefore claims older than this may be stolen
     * @return 1 if this worker won the claim, otherwise 0
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Order o
            SET o.paymentClaimedAt = :now, o.paymentClaimedBy = :worker, o.updatedAt = :now
            WHERE o.id = :id
              AND o.status = com.orders.service.domain.OrderStatus.PENDING_PAYMENT
              AND (o.paymentClaimedAt IS NULL OR o.paymentClaimedAt < :staleBefore)
            """)
    int claimForPayment(
            @Param("id") UUID id,
            @Param("now") Instant now,
            @Param("worker") String worker,
            @Param("staleBefore") Instant staleBefore);

    /**
     * Unexpired pending orders that still have payment attempts left and are unclaimed (or stale).
     *
     * @param now          current time
     * @param maxAttempts  configured retry cap
     * @param staleBefore  reclaim threshold
     * @return payable order ids
     */
    @Query("""
            SELECT o.id FROM Order o
            WHERE o.status = com.orders.service.domain.OrderStatus.PENDING_PAYMENT
              AND o.expiresAt > :now
              AND o.paymentAttempts < :maxAttempts
              AND (o.paymentClaimedAt IS NULL OR o.paymentClaimedAt < :staleBefore)
            ORDER BY o.createdAt
            """)
    List<UUID> findPayableIds(
            @Param("now") Instant now,
            @Param("maxAttempts") int maxAttempts,
            @Param("staleBefore") Instant staleBefore);
}
