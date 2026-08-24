package com.javatutorial.order_service.service;

import com.javatutorial.order_service.client.ProductClient;
import com.javatutorial.order_service.config.RabbitConfig;
import com.javatutorial.order_service.dto.CreateOrderRequest;
import com.javatutorial.order_service.event.OrderCreatedEvent;
import com.javatutorial.order_service.exception.OrderNotFoundException;
import com.javatutorial.order_service.exception.ProductNotFoundException;
import com.javatutorial.order_service.model.Order;
import com.javatutorial.order_service.repository.OrderRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final ProductClient productClient;
    private final RabbitTemplate rabbitTemplate;

    public OrderService(OrderRepository orderRepository, ProductClient productClient, RabbitTemplate rabbitTemplate) {
        this.orderRepository = orderRepository;
        this.productClient = productClient;
        this.rabbitTemplate = rabbitTemplate;
    }

    // Deliberately NOT @Transactional: the remote calls below must not run inside an open
    // transaction, or a slow product-service would hold a DB connection hostage. The single
    // save() at the end is transactional on its own, so atomicity is not lost.
    public Order create(Long userId, String username, List<CreateOrderRequest.Item> items) {
        List<Long> productIds = items.stream().map(CreateOrderRequest.Item::productId).toList();
        // One call for the whole order instead of one per line - the distributed N+1 fix
        Map<Long, ProductClient.ProductView> productsById = productClient.findByIds(productIds).stream()
                .collect(Collectors.toMap(ProductClient.ProductView::id, product -> product));

        List<Order.Line> lines = items.stream().map(item -> {
            // The batch endpoint omits ids it could not find, so an absent key means
            // the product does not exist - the same error the caller got before.
            ProductClient.ProductView product = productsById.get(item.productId());
            if (product == null) {
                throw new ProductNotFoundException(item.productId());
            }

            return new Order.Line(product.id(), product.name(), product.price(), item.quantity());
        }).toList();

        // return orderRepository.save(Order.create(userId, username, lines));
        Order saved = orderRepository.save(Order.create(userId, username, lines));

        // Published AFTER save() - create() is deliberately not @Transactional (Faz 6),
        // so save() has already committed by this point. Publishing before commit could
        // notify about an order that then never existed. Still not atomic with the DB
        // write though: publish can fail after a successful save. That gap is what
        // the outbox pattern (Faz 7.5) exists to close - here we just accept it.
        rabbitTemplate.convertAndSend(
                RabbitConfig.ORDER_EVENTS_EXCHANGE,
                RabbitConfig.ORDER_CREATED_ROUTING_KEY,
                new OrderCreatedEvent(UUID.randomUUID(), saved.getId(), saved.getUserId(), saved.totalPrice(), Instant.now())
        );

        return saved;
    }

    @Transactional
    public Order markReturned(Long orderId, Long userId) {
        Order order = findOwned(orderId, userId);
        order.markReturned(); // the rule lives on the entity
        return order; // dirty checking flushes the change; no explicit save needed
    }

    @Transactional(readOnly = true)
    public Page<Order> findMine(Long userId, Pageable pageable) {
        return orderRepository.findByUserId(userId, pageable);
    }

    @Transactional(readOnly = true)
    public Order findOwned(Long orderId, Long userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        // 404, not 403, for someone else's order: confirming that it exists is itself a leak
        if (!order.getUserId().equals(userId)) {
            throw new OrderNotFoundException(orderId);
        }
        return order;
    }
}