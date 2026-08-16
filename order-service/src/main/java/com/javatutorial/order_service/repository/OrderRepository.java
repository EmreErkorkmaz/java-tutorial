package com.javatutorial.order_service.repository;

import com.javatutorial.order_service.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // items is LAZY, so any query that will read the lines asks for them up front.
    // Same N+1 lesson as product/category - the fetch plan belongs to the query.
    @EntityGraph(attributePaths = "items")
    Page<Order> findByUserId(Long userId, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "items")
    Optional<Order> findById(Long id);
}
