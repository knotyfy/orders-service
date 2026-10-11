package com.orders.service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Line item frozen at create time so later catalog price/name changes do not rewrite history.
 */
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private String sku;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    /** JPA constructor. */
    protected OrderItem() {
    }

    /**
     * Copies SKU, name, and unit price from the catalog product.
     *
     * @param product  locked catalog row
     * @param quantity reserved units
     * @return new line item (order association set via {@link Order#addItem})
     */
    public static OrderItem snapshot(Product product, int quantity) {
        OrderItem item = new OrderItem();
        item.id = UUID.randomUUID();
        item.productId = product.getId();
        item.sku = product.getSku();
        item.productName = product.getName();
        item.quantity = quantity;
        item.unitPrice = product.getUnitPrice();
        return item;
    }

    /**
     * Sets the owning order (called from {@link Order#addItem}).
     *
     * @param order parent order
     */
    void setOrder(Order order) {
        this.order = order;
    }

    /** @return line-item id */
    public UUID getId() {
        return id;
    }

    /** @return catalog product id at create time */
    public UUID getProductId() {
        return productId;
    }

    /** @return SKU snapshot */
    public String getSku() {
        return sku;
    }

    /** @return name snapshot */
    public String getProductName() {
        return productName;
    }

    /** @return reserved quantity */
    public int getQuantity() {
        return quantity;
    }

    /** @return unit price snapshot */
    public BigDecimal getUnitPrice() {
        return unitPrice;
    }
}
