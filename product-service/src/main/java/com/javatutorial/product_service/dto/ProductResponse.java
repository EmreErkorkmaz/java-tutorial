package com.javatutorial.product_service.dto;

import com.javatutorial.product_service.model.Product;

import java.math.BigDecimal;

public record ProductResponse (Long id, String name, BigDecimal price, String categoryName) {
    // entity -> DTO conversion lives in one place so the controller does not repeat it everywhere

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getCategory().getName() // this line is what will trigger the N+1
        );
    }
}
