package com.javatutorial.product_service.model;

import jakarta.persistence.*;

@Entity
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100, nullable = false) // must match VARCHAR(100) in the migration:
    private String name; // ddl-auto=validate compares entity and table

    protected Category () {} // required by JPA; protected keeps it out of our own code

    public Category(String name) {
        this.name = name;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
