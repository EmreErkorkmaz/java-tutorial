package com.javatutorial.product_service.exception;

public class TooManyAttemptsException extends RuntimeException{
    public TooManyAttemptsException(String username) {
        super("Too many failed login attempts for: " + username);
    }
}
