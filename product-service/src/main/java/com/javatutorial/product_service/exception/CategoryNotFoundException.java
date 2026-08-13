package com.javatutorial.product_service.exception;

public class CategoryNotFoundException extends RuntimeException {
    public CategoryNotFoundException(Long id) {
        super("Category not found: " + id);
    }
}
