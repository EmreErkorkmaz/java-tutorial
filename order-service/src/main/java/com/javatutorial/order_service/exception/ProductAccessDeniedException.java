package com.javatutorial.order_service.exception;

public class ProductAccessDeniedException extends RuntimeException {
    public ProductAccessDeniedException(int status) {
        super("product-service rejected our call with status " + status);
    }
}