package com.javatutorial.order_service.service;

import com.javatutorial.order_service.client.ProductClient;
import com.javatutorial.order_service.dto.CreateOrderRequest;
import com.javatutorial.order_service.exception.OrderAlreadyReturnedException;
import com.javatutorial.order_service.exception.OrderNotFoundException;
import com.javatutorial.order_service.model.Order;
import com.javatutorial.order_service.model.OrderStatus;
import com.javatutorial.order_service.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock // the remote call is mocked: this test is about our logic, not about HTTP
    private ProductClient productClient;

    @InjectMocks
    private OrderService orderService;

    @Test
    void create_takesPriceFromProductService_notFromTheClient() {
        when(productClient.findById(11L))
                .thenReturn(new ProductClient.ProductView(11L, "Keyboard", new BigDecimal("1200.50")));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order order = orderService.create(1L, "emre",
                List.of(new CreateOrderRequest.Item(11L, 2)));

        // the request only carried productId and quantity - name and price came from the remote call
        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getItems().getFirst().getUnitPrice()).isEqualByComparingTo("1200.50");
        assertThat(order.totalPrice()).isEqualByComparingTo("2401.00"); // 1200.50 x 2
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    void markReturned_whenAlreadyReturned_throws() {
        Order order = Order.create(1L, "emre",
                List.of(new Order.Line(11L, "Keyboard", new BigDecimal("10.00"), 1)));
        order.markReturned(); // first return succeeds
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.markReturned(1L, 1L))
                .isInstanceOf(OrderAlreadyReturnedException.class);
    }

    @Test
    void findOwned_whenOrderBelongsToAnotherUser_behavesAsIfMissing() {
        Order order = Order.create(1L, "emre",
                List.of(new Order.Line(11L, "Keyboard", new BigDecimal("10.00"), 1)));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        // user 2 asking for user 1's order: NotFound, not Forbidden - saying "it exists but
        // is not yours" would leak which order ids are real
        assertThatThrownBy(() -> orderService.findOwned(1L, 2L))
                .isInstanceOf(OrderNotFoundException.class);
    }
}