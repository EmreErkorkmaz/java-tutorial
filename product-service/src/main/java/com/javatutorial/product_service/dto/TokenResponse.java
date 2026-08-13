package com.javatutorial.product_service.dto;

// tokenType tells the client how to build the header: "Authorization: Bearer <token>"
public record TokenResponse(String token, String tokenType, long expiresInSeconds) {
}