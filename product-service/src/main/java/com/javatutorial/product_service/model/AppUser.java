package com.javatutorial.product_service.model;

import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "app_user")
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50, nullable = false, unique = true)
    private String username;

    @Column(length = 100, nullable = false)
    private String password;

    @Column(nullable = false)
    private boolean enabled = true;

    // EAGER on purpose: authentication always needs the roles, and the set is tiny.
    // Spring Security loads users outside our transactions, and with open-in-view off
    // a lazy set here would blow up with LazyInitializationException.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_role", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role")
    @Enumerated(EnumType.STRING) // store the enum name, not its ordinal - reordering the enum must not corrupt data
    private Set<Role> roles = new HashSet<>();

    protected AppUser() {}

    public AppUser(String username, String password, Set<Role> roles) {
        this.username = username;
        this.password = password;
        this.roles = roles;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public boolean isEnabled() { return enabled; }
    public Set<Role> getRoles() { return roles; }
}
