package com.orders.service.repo;

import com.orders.service.domain.Product;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for catalog products, including pessimistic locks used at order create and restock.
 */
public interface ProductRepository extends JpaRepository<Product, UUID> {

    /**
     * Loads a product by unique SKU without locking.
     *
     * @param sku catalog SKU
     * @return product if present
     */
    Optional<Product> findBySku(String sku);

    /**
     * Locks catalog rows in SKU order so concurrent creates cannot deadlock.
     *
     * @param skus SKUs to lock
     * @return locked products in ascending SKU order
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT p FROM Product p WHERE p.sku IN :skus ORDER BY p.sku ASC")
    List<Product> lockBySkusOrderBySku(@Param("skus") Collection<String> skus);
}
