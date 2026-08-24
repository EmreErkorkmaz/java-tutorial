package com.javatutorial.order_service.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// eventId lets a consumer detect redelivery of the same event (Faz 7.3 idempotency demo) -
// at-least-once delivery means the exact same event can legitimately arrive twice.
public record OrderCreatedEvent(
        UUID eventId,
        Long orderId,
        Long userId,
        BigDecimal totalAmount,
        Instant createdAt
) {}
