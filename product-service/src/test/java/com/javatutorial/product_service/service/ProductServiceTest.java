package com.javatutorial.product_service.service;

import com.javatutorial.product_service.exception.CategoryNotFoundException;
import com.javatutorial.product_service.exception.ProductNotFoundException;
import com.javatutorial.product_service.model.Category;
import com.javatutorial.product_service.model.Product;
import com.javatutorial.product_service.repository.CategoryRepository;
import com.javatutorial.product_service.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class) // hooks Mockito into JUnit; populates the @Mock fields
class ProductServiceTest {          // no Spring context here -> runs in milliseconds

    @Mock // fake repository: the DB is never touched
    private ProductRepository productRepository;

    @Mock // the service resolves categories through this one
    private CategoryRepository categoryRepository;

    @InjectMocks // builds the REAL ProductService, passing the mock through its constructor
    private ProductService productService;

    private final Category electronics = new Category("Electronics");

    @Test
    void findById_whenProductExists_returnsProduct() {
        Product product = new Product("Keyboard", new BigDecimal("1200.50"), electronics);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product)); // stub: answer this call with this value

        Product result = productService.findById(1L);

        assertThat(result.getName()).isEqualTo("Keyboard");
    }

    @Test
    void findById_whenProductMissing_throwsException() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty()); // "not in the DB" scenario

        assertThatThrownBy(() -> productService.findById(999L)) // lambda defers the call so the assertion can catch it
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    void update_whenProductExists_overwritesFields() {
        Product existing = new Product("Old", new BigDecimal("100"), electronics);
        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(new Category("Accessories")));
        when(productRepository.save(any(Product.class)))
                .thenAnswer(inv -> inv.getArgument(0)); // make save() return whatever it was given

        Product result = productService.update(1L, "New", new BigDecimal("250"), 2L);

        assertThat(result.getName()).isEqualTo("New");
        assertThat(result.getPrice()).isEqualByComparingTo("250"); // BigDecimal.equals also compares scale -> compare by value
        assertThat(result.getCategory().getName()).isEqualTo("Accessories");
    }

    @Test
    void create_whenCategoryMissing_throwsException() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.create("Mouse", new BigDecimal("300"), 99L))
                .isInstanceOf(CategoryNotFoundException.class)
                .hasMessageContaining("99");
        // the product is never saved: resolveCategory() throws before save() is reached
    }
}
