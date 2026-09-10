package com.javatutorial.order_service.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String ORDER_EVENTS_EXCHANGE = "order.events";
    public static final String ORDER_CREATED_ROUTING_KEY = "order.created";
    public static final String ORDER_CREATED_QUEUE = "order.created.queue";
    public static final String ORDER_CREATED_DLQ = "order.created.dlq";


    // Topic exchange routes by pattern match on the routing key (e.g. "order.*"), not a
    // fixed queue name - other services can later bind their own queue to this same
    // exchange without order-service knowing they exist. That's the decoupling itself.
    @Bean
    TopicExchange orderEventsExchange() {
        return new TopicExchange(ORDER_EVENTS_EXCHANGE);
    }

    //@Bean
    // Queue orderCreatedQueue() {
    //return new Queue(ORDER_CREATED_QUEUE, true); // durable: survives a broker restart
    // }

    @Bean
    Queue orderCreatedQueue() {
        return QueueBuilder.durable(ORDER_CREATED_QUEUE)
                // "" = nameless/default exchange. Onda routing key = queue adı demektir,
                // yani ayrı bir DLX exchange + binding tanımlamaya gerek yok.
                .deadLetterExchange("")
                .deadLetterRoutingKey(ORDER_CREATED_DLQ)
                .build();
    }

    @Bean
    Queue orderCreatedDlq() {
        return QueueBuilder.durable(ORDER_CREATED_DLQ).build();
    }

    @Bean
    Binding orderCreatedBinding(Queue orderCreatedQueue, TopicExchange orderEventsExchange) {
        return BindingBuilder.bind(orderCreatedQueue).to(orderEventsExchange).with(ORDER_CREATED_ROUTING_KEY);
    }

    // Default converter only handles String/byte[]. This one serializes with Jackson,
    // so rabbitTemplate.convertAndSend(...) can take a record/POJO directly.
    // Spring Boot's autoconfigured RabbitTemplate picks up this bean automatically.
    @Bean
    MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
