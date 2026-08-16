package com.javatutorial.order_service.model;

import com.javatutorial.order_service.exception.OrderAlreadyReturnedException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customer_order") // "order" is a reserved word in SQL
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId; // identity, taken from the JWT subject

    @Column(length = 50, nullable = false)
    private String username; // display snapshot only - never used for matching

    @Enumerated(EnumType.STRING) // never ORDINAL: reordering the enum would corrupt existing rows
    @Column(length = 20, nullable = false)
    private OrderStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant returnedAt; // null until returned

    // Items have no life outside their order (composition):
    //   cascade = ALL     -> saving the order saves its items
    //   orphanRemoval     -> removing an item from this list deletes its row
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {}

    private Order(Long userId, String username) {
        this.userId = userId;
        this.username = username;
        this.status = OrderStatus.CREATED;
        this.createdAt = Instant.now();
    }

    // The only way to build an order: assembled once, complete, and never edited after.
    // Line data (name, price) is resolved from product-service before this is called -
    // an entity does no I/O of its own.
    public static Order create(Long userId, String username, List<Line> lines) {
        Order order = new Order(userId, username);
        for (Line line : lines) {
            // private access is per class, not per instance, so a static method may
            // reach into the object it just built
            order.items.add(new OrderItem(order, line.productId(), line.productName(),
                    line.unitPrice(), line.quantity()));
        }
        return order;
    }

    // Carrier for one assembled line - not an entity, just the data needed to build one
    public record Line(Long productId, String productName, BigDecimal unitPrice, int quantity) {}

    // The rule lives on the entity that owns the state, so no caller can bypass it.
    public void markReturned() {
        if (status == OrderStatus.RETURNED) {
            throw new OrderAlreadyReturnedException(id);
        }
        this.status = OrderStatus.RETURNED;
        this.returnedAt = Instant.now();
    }

    public BigDecimal totalPrice() {
        return items.stream()
                .map(OrderItem::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add); // starts at zero, adds each line
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
    public OrderStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReturnedAt() { return returnedAt; }

    // Unmodifiable: items are added through addItem() so the order stays in control
    public List<OrderItem> getItems() { return List.copyOf(items); }
}
