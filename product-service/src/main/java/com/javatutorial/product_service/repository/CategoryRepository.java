package com.javatutorial.product_service.repository;

import com.javatutorial.product_service.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
}