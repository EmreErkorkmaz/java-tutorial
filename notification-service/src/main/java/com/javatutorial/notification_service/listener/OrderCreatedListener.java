package com.javatutorial.notification_service.listener;

import com.javatutorial.notification_service.event.OrderCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


@Component
public class OrderCreatedListener {

    private final Set<UUID> seen = ConcurrentHashMap.newKeySet();

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedListener.class);
    // @QueueBinding declares the same exchange/queue/binding order-service's RabbitConfig
    // already declared (Faz 7.1) - RabbitMQ treats a repeat declaration with identical
    // properties as a no-op, not a conflict. So both services state the topology they
    // depend on independently, without either importing the other's code.

    @RabbitListener(
            bindings = @QueueBinding(value = @Queue(name = "order.created.queue", durable = "true", arguments = {
                    @Argument(name = "x-dead-letter-exchange", value = ""),
                    @Argument(name = "x-dead-letter-routing-key", value = "order.created.dlq")
            }),
                    exchange = @Exchange(name = "order.events", type = ExchangeTypes.TOPIC),
                    key = "order.created")
    )
    public void handle(OrderCreatedEvent event) {
        if (!seen.add(event.eventId())) {
            log.info("Duplicate skipped for order {} (eventId {})", event.orderId(), event.eventId());
            return;
        }
        log.info("Notifying user {} about order {} (total: {})",
                event.userId(), event.orderId(), event.totalAmount());
    }
}
