package com.javatutorial.product_service.service;

import com.javatutorial.product_service.exception.UsernameAlreadyExistsException;
import com.javatutorial.product_service.model.AppUser;
import com.javatutorial.product_service.model.Role;
import com.javatutorial.product_service.repository.AppUserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;

@Service
@Transactional(readOnly = true)
public class AppUserService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AppUserService(AppUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AppUser register(String username, String rawPassword) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new UsernameAlreadyExistsException(username); // fast, friendly answer for the common case
        }

        try {
            return userRepository.save(new AppUser(
                    username,
                    passwordEncoder.encode(rawPassword), // the raw password is never stored, logged or returned
                    EnumSet.of(Role.ROLE_USER)));        // everyone starts as a plain user
        } catch (DataIntegrityViolationException e) {
            // The check above is NOT atomic: two concurrent requests can both pass it and
            // both reach save(). The UNIQUE constraint on username is the real guard, so we
            // translate its failure into the same domain exception.
            throw new UsernameAlreadyExistsException(username);
        }
    }
}
