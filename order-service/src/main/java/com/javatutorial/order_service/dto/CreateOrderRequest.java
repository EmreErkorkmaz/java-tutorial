package com.javatutorial.order_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

// No price and no username here: both are decided by the server. A client that could
// send its own price could buy anything for one lira.
public record CreateOrderRequest(

        @NotEmpty(message = "At least one item is required")
        @Valid // cascades validation INTO each item - without it the item rules are skipped
        List<Item> items
) {

    public record Item(
            @NotNull(message = "Product id is required")
            Long productId,

            @NotNull(message = "Quantity is required")
            @Positive(message = "Quantity must be greater than 0")
            Integer quantity
    ) {
    }
}
