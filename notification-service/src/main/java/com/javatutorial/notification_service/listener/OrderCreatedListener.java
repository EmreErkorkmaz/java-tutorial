package com.javatutorial.notification_service.listener;

import com.javatutorial.notification_service.event.OrderCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;


@Component
public class OrderCreatedListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedListener.class);
    // @QueueBinding declares the same exchange/queue/binding order-service's RabbitConfig
    // already declared (Faz 7.1) - RabbitMQ treats a repeat declaration with identical
    // properties as a no-op, not a conflict. So both services state the topology they
    // depend on independently, without either importing the other's code.

    @RabbitListener(
            bindings = @QueueBinding(value = @Queue(name = "order.created.queue", durable = "true"),
                    exchange = @Exchange(name = "order.events", type = ExchangeTypes.TOPIC),
                    key = "order.created")
    )
    public void handle(OrderCreatedEvent event) {
        log.info("Notifying user {} about order {} (total: {})", event.userId(), event.orderId(), event.totalAmount());
    }
}
