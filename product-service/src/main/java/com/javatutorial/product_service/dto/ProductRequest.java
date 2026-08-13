package com.javatutorial.product_service.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// no id here: the id never arrives in the request body, it comes from the URL
public record ProductRequest(
        @NotBlank(message = "Name can not be blank") // rejects null, "" and "   " (@NotNull only catches null)
        String name,

        @NotNull(message = "Price is required")                  // BigDecimal is an object, so it can be null
        @Positive(message = "Price must be bigger than 0")    // rejects zero and negative values
        BigDecimal price,

        @NotNull(message = "Category is required")
        Long categoryId
) {

}
