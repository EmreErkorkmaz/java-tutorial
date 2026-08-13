package com.javatutorial.product_service.model;

// Spring Security's hasRole() checks expect the ROLE_ prefix, so we store the full name
public enum Role {
    ROLE_USER,
    ROLE_ADMIN
}