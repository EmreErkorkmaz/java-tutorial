package com.javatutorial.order_service.controller;

import com.javatutorial.order_service.config.JwtConfig;
import com.javatutorial.order_service.config.SecurityConfig;
import com.javatutorial.order_service.model.Order;
import com.javatutorial.order_service.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, JwtConfig.class}) // the slice does not scan @Configuration classes
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    private static final String VALID_BODY = """
            {"items":[{"productId":11,"quantity":2}]}
            """;

    @Test
    void create_whenAnonymous_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized()); // no token: orders are never public
    }

    @Test
    void create_whenAuthenticated_returnsCreated() throws Exception {
        Order order = Order.create(1L, "emre",
                List.of(new Order.Line(11L, "Keyboard", new BigDecimal("1200.50"), 2)));
        when(orderService.create(anyLong(), anyString(), any())).thenReturn(order);

        mockMvc.perform(post("/api/orders")
                        // jwt() puts a decoded token in the SecurityContext without any real
                        // signature - the controller reads sub and username exactly as in production
                        .with(jwt().jwt(token -> token.subject("1").claim("username", "emre")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalPrice").value(2401.00))
                .andExpect(jsonPath("$.items[0].productName").value("Keyboard"));
    }

    @Test
    void create_whenItemsEmpty_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .with(jwt().jwt(token -> token.subject("1").claim("username", "emre")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[]}
                                """))
                .andExpect(status().isBadRequest()) // @NotEmpty
                .andExpect(jsonPath("$.fields.items").exists());
    }
}