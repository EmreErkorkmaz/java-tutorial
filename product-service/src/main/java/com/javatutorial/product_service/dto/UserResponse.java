package com.javatutorial.product_service.dto;

import com.javatutorial.product_service.model.AppUser;
import com.javatutorial.product_service.model.Role;

import java.util.Set;
import java.util.stream.Collectors;

// no password field anywhere in here - not even the hash leaves the server
public record UserResponse(Long id, String username, Set<String> roles) {

    public static UserResponse from(AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getRoles().stream().map(Role::name).collect(Collectors.toSet()));
    }
}
