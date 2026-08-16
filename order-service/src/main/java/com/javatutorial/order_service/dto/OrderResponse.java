package com.javatutorial.order_service.dto;

import com.javatutorial.order_service.model.Order;
import com.javatutorial.order_service.model.OrderItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(Long id, String status, BigDecimal totalPrice,
                            Instant createdAt, Instant returnedAt, List<Item> items) {

    public record Item(Long productId, String productName,
                       BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {

        static Item from(OrderItem item) {
            return new Item(item.getProductId(), item.getProductName(),
                    item.getUnitPrice(), item.getQuantity(), item.lineTotal());
        }
    }

    // Reads the items collection, so it must run while the order is still attached -
    // items is LAZY and open-in-view is off.
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getStatus().name(),
                order.totalPrice(),
                order.getCreatedAt(),
                order.getReturnedAt(),
                order.getItems().stream().map(Item::from).toList());
    }
}
