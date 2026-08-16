package com.javatutorial.order_service.exception;

public class OrderAlreadyReturnedException extends RuntimeException {
    public OrderAlreadyReturnedException(Long id) {
        super("Order already returned: " + id);
    }
}