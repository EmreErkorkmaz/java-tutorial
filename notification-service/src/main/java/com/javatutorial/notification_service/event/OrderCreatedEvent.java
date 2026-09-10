package com.javatutorial.notification_service.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// Deliberately NOT shared with order-service.event.OrderCreatedEvent via a common library -
// the two services agree on a JSON wire contract, not on compiled Java code. Sharing the
// class would mean deploying both services in lockstep whenever the event shape changes,
// which defeats the point of them being independently deployable (same reasoning as
// order-service snapshotting product name/price instead of importing product-service's entity).
public record OrderCreatedEvent(
        UUID eventId,
        Long orderId,
        Long userId,
        BigDecimal totalAmount,
        Instant createdAt
) {}
