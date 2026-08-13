package com.javatutorial.product_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableMethodSecurity // without this, @PreAuthorize annotations are silently ignored
public class SecurityConfig {
    // Defining this bean makes Boot's default chain back off (@ConditionalOnMissingBean)
    @Bean
    SecurityFilterChain filterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter
    ) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        // rules are evaluated top to bottom, FIRST match wins
                        // registration must be reachable without credentials, by definition
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        .anyRequest().authenticated()
                )
                .cors(Customizer.withDefaults())
                // No server-side session: every request must carry its own proof of identity.
                // Without this Spring still creates a JSESSIONID, which defeats the point of
                // stateless tokens and would not survive more than one instance behind a load balancer.
                // .httpBasic(Customizer.withDefaults()) // temporary: JWT replaces this later
                // .csrf(csrf -> csrf.disable()).build();
                .sessionManagement(
                        session ->
                                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(
                        oauth ->
                                oauth.jwt(jwt ->
                                        jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)
                                )
                ).csrf(csrf -> csrf.disable())
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${security.cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setMaxAge(Duration.ofHours(1)); // how long the browser may cache the preflight answer
        // config.setAllowCredentials(true); // only if you switch to cookie-based auth
        // Note: allowCredentials(true) cannot be combined with allowedOrigins("*") -
        // the browser rejects that pairing outright.

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(); // salt is generated per hash and stored inside it
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles"); // default looks at "scope"/"scp"
        authorities.setAuthorityPrefix(""); // our claim already carries the ROLE_ prefix

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    // Temporary in-memory user. The next step replaces this with a DB-backed UserDetailsService.
    //    @Bean
    //    UserDetailsService userDetailsService(PasswordEncoder encoder) {
    //        return new InMemoryUserDetailsManager(
    //                User.withUsername("admin")
    //                        .password(encoder.encode("admin123"))
    //                        .roles("ADMIN")
    //                        .build()
    //        );
    //    }

    // Exposed so the login endpoint can run the same authentication logic HTTP Basic uses:
    // load the user, compare the BCrypt hash, apply the enabled/locked checks.
    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}