package com.javatutorial.order_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter
    ) throws Exception {
        return http.authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers("/actuator/health/**", "/error").permitAll()
                                        // Unlike products, an order is never public: there is no anonymous read here
                                        .anyRequest().authenticated())
                .sessionManagement(
                        session ->
                                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(
                        oauth ->
                                oauth.jwt(jwt ->
                                        jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .csrf(csrf -> csrf.disable()) // no cookies are involved, so there is nothing to forge
                .build();
    }

    // Same mapping as product-service: our claim is "roles" and already carries the ROLE_ prefix
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
