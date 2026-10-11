package com.orders.service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Catalog product: SKU, live unit price, and stock decremented at order create.
 */
@Entity
@Table(name = "products")
public class Product {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private int stock;

    @Version
    private long version;

    /** JPA constructor. */
    protected Product() {
    }

    /** @return product id */
    public UUID getId() {
        return id;
    }

    /** @return unique SKU */
    public String getSku() {
        return sku;
    }

    /** @return display name */
    public String getName() {
        return name;
    }

    /** @return current catalog unit price */
    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    /** @return ISO currency code */
    public String getCurrency() {
        return currency;
    }

    /** @return units available to sell */
    public int getStock() {
        return stock;
    }

    /**
     * Removes units from stock after a successful reservation check.
     *
     * @param quantity units to reserve
     */
    public void decrementStock(int quantity) {
        this.stock -= quantity;
    }

    /**
     * Returns units to stock when an order is cancelled.
     *
     * @param quantity units to restore
     */
    public void incrementStock(int quantity) {
        this.stock += quantity;
    }

    /**
     * Overwrites stock (test fixtures / catalog reset).
     *
     * @param stock new on-hand quantity
     */
    public void setStock(int stock) {
        this.stock = stock;
    }
}
