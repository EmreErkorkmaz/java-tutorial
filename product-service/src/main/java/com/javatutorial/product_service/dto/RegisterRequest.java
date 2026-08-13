package com.javatutorial.product_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
        String username,

        @NotBlank(message = "Password is required")
        // 72 is not arbitrary: BCrypt silently ignores anything past 72 bytes, so a longer
        // password would give the user a false sense of strength
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        String password
) {
}
