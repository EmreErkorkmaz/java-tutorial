package com.javatutorial.order_service.exception;

public class ProductServiceUnavailableException extends RuntimeException {
    public ProductServiceUnavailableException(Throwable cause) {
        super("Product service is unavailable", cause);
    }
}