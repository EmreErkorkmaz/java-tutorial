package com.javatutorial.order_service.controller;

import com.javatutorial.order_service.dto.CreateOrderRequest;
import com.javatutorial.order_service.dto.OrderResponse;
import com.javatutorial.order_service.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@Valid @RequestBody CreateOrderRequest request, @AuthenticationPrincipal Jwt jwt) {
        return OrderResponse.from(orderService.create(userId(jwt), username(jwt), request.items()));
    }

    @GetMapping
    public Page<OrderResponse> myOrders(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)Pageable pageable,
            @AuthenticationPrincipal Jwt jwt
    ) {
        // always scoped to the caller: there is no "all orders" endpoint by design
        return orderService.findMine(userId(jwt), pageable).map(OrderResponse::from);
    }

    @GetMapping("/{id}")
    public OrderResponse getById(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return OrderResponse.from(orderService.findOwned(id, userId(jwt)));
    }

    @PostMapping("/{id}/return")
    public OrderResponse markReturned(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return OrderResponse.from(orderService.markReturned(id, userId(jwt)));
    }


    // Identity comes ONLY from the verified token. If the client could send a userId in the
    // body or a query param, anyone could order and read on someone else's behalf.
    private Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject()); // sub is the user id, set by product-service
    }

    private String username(Jwt jwt) {
        return jwt.getClaimAsString("username");
    }

}