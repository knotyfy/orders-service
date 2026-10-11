package com.orders.service.api;

import com.orders.service.api.dto.ProductResponse;
import com.orders.service.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP API for reading catalog products and current stock.
 */
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final OrderService orderService;

    /**
     * @param orderService service used to load a product by SKU
     */
    public ProductController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Returns catalog price and live stock for one SKU (used to prove decrement/restock).
     *
     * @param sku product SKU
     * @return the product, or 404 if unknown
     */
    @GetMapping("/{sku}")
    public ProductResponse get(@PathVariable String sku) {
        return orderService.getProduct(sku);
    }
}
