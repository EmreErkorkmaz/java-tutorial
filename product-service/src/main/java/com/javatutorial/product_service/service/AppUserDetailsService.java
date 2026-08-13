package com.javatutorial.product_service.service;

import com.javatutorial.product_service.model.AppUser;
import com.javatutorial.product_service.repository.AppUserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {
    private final AppUserRepository userRepository;

    public AppUserDetailsService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(username));

        // adapter: AppUser stays a plain domain object, this method translates it
        // into the contract Spring Security understands
        return User.withUsername(user.getUsername())
                .password(user.getPassword())
                .disabled(!user.isEnabled()) // builder wants "is it disabled", the entity stores "is it enabled"
                .authorities(user.getRoles().stream().map(role -> new SimpleGrantedAuthority(role.name()))
                        .toList())
                .build();
    }
}
