package com.javatutorial.product_service.model;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity                          // marks this class as a DB table. Table name: PRODUCT
public class Product {
    @Id // this field is the primary key
    @GeneratedValue(strategy = GenerationType.IDENTITY) // let the DB generate the id (auto-increment)

    private Long id;

    private String name;

    private BigDecimal price; // money: never double, always BigDecimal (other types round decimals)

    @ManyToOne(fetch = FetchType.LAZY) // @ManyToOne defaults to EAGER - we override it on purpose
    @JoinColumn(name = "category_id", nullable = false) // the FK column lives on THIS table
    private Category category;

    public Product() {} // JPA needs a no-arg constructor to instantiate the entity itself (MANDATORY)

    public Product(String name, BigDecimal price, Category category) { // convenience constructor for our own code
        this.name = name;
        this.price = price;
        this.category = category;
    }

    // getters / setters — encapsulation: fields stay private, access goes through methods
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getPrice() {return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
}
