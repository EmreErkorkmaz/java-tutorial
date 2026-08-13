package com.javatutorial.product_service.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import com.javatutorial.product_service.model.Product;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    //                                                    ^ entity type, ^ id type (generics)

    // The METHOD NAME is the query. Spring derives this SQL from "findByName":
    // SELECT * FROM product WHERE name = ?
    List<Product> findByName(String name);
    List<Product> findByNameContaining(String search);
    List<Product> findByPriceLessThan(BigDecimal price);

    // findAll() already exists in JpaRepository - we redeclare it only to attach the graph.
    // attributePaths lists the LAZY associations THIS query should load up front with a JOIN,
   // instead of leaving proxies behind that fire one query each when touched later.
    // The paged overload also comes from JpaRepository; same trick, we redeclare it to
    // attach the graph so a page of products still costs a single query.
    @Override
    @EntityGraph(attributePaths = "category")
    Page<Product> findAll(Pageable pageable);

    // same treatment for the single-item lookup: without the graph its category stays a
    // proxy, and with OSIV off there is no session left to initialise it during DTO mapping
    @Override
    @EntityGraph(attributePaths = "category")
    Optional<Product> findById(Long id);
}
