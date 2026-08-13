package com.javatutorial.product_service;

import com.javatutorial.product_service.model.Category;
import com.javatutorial.product_service.repository.CategoryRepository;
import com.javatutorial.product_service.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest                            // full context: real service, real repositories
@Import(TestcontainersConfiguration.class) // against a throwaway Postgres, not the dev database
class ProductServiceIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    void createCategoryWithProduct_whenProductInsertFails_rollsBackTheCategory() {
        assertThatThrownBy(() -> productService.createCategoryWithProduct(
                "Orphan test", "Bad product", new BigDecimal("-1")))
                .isInstanceOf(DataIntegrityViolationException.class); // CHECK (price > 0) from V1 fires

        // the category was already committed by its own transaction - this is the bug
        //assertThat(categoryRepository.findAll())
                //.extracting(Category::getName)
                //.contains("Orphan test");

        assertThat(categoryRepository.findAll())
                .extracting(Category::getName)
                .doesNotContain("Orphan test"); // the failed product insert took the category down with it
    }
}