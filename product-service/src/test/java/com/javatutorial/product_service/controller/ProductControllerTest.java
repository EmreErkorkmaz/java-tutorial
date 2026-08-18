package com.javatutorial.product_service.controller;

import com.javatutorial.product_service.config.JwtConfig;
import com.javatutorial.product_service.config.SecurityConfig;
import com.javatutorial.product_service.model.Category;
import com.javatutorial.product_service.model.Product;
import com.javatutorial.product_service.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest; // Boot 4 package (moved from boot.test.autoconfigure.web.servlet)
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean; // replaces the removed @MockBean
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(ProductController.class) // web layer ONLY: no JPA, no DB, no real service
@Import({SecurityConfig.class, JwtConfig.class}) // the slice ignores @Configuration classes - pull our real rules in
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc; // sends simulated HTTP requests without opening a real port

    @MockitoBean // swaps the ProductService bean in the context for a mock
    private ProductService productService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    @WithMockUser // reads need a token now too - this test is about the JSON contract, not auth
    void getAll_returnsProductListAsJson() throws Exception {
        Product product = new Product("Keyboard", new BigDecimal("1200.50"), new Category("Electronics"));
        // when(productService.findAll())
        //        .thenReturn(List.of(new Product("Keyboard", new BigDecimal("1200.50"), new Category("Electronics"))));
        Page<Product> page = new PageImpl<>(List.of(product), Pageable.ofSize(20), 1);

        when(productService.findAll(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                // .andExpect(jsonPath("$[0].name").value("Keyboard"))
                // .andExpect(jsonPath("$[0].categoryName").value("Electronics")); // assert the JSON contract, not the internals
                .andExpect(jsonPath("$.content[0].name").value("Keyboard"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getAll_whenAnonymous_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isUnauthorized());
        // catalog reads are no longer public: this locks that decision in
    }

    @Test
    @WithMockUser // authenticated caller: this test is about the validation rule, not about auth
    void create_whenPayloadInvalid_returnsBadRequestWithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "   ", "price": 100, "categoryId": 1}
                                """)) // categoryId is valid on purpose: this test is about the name rule
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.name").exists()); // is GlobalExceptionHandler wired in?
        // no stubbing needed: validation fails before the controller method body runs
    }

    @Test
    void create_whenAnonymous_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/products").content("""
                        {"name": "Keyboard", "price": 10.00, "categoryId": 1}
                        """))
                .andExpect(status().isUnauthorized());
        // no @WithMockUser here: the missing identity IS the thing under test
    }

    @Test
    @WithMockUser(roles = "USER") // authenticated, but without ROLE_ADMIN
    void delete_whenNotAdmin_returnsForbidden() throws Exception {
        mockMvc.perform(delete("/api/products/1").with(csrf()))
                .andExpect(status().isForbidden()); // 403, not 401: the identity is fine, the role is not
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_whenAdmin_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/products/1").with(csrf()))
                .andExpect(status().isNoContent());
    }
}
