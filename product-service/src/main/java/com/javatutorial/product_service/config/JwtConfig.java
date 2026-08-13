package com.javatutorial.product_service.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration
public class JwtConfig {

    private final SecretKey key;

    public JwtConfig(@Value("${security.jwt.secret}") String secret) {
        // HS256 is symmetric: the SAME key both signs and verifies. That is why a second
        // service could not verify our tokens without being handed this secret - the
        // reason RS256 exists, and the decision we revisit in phase 6.
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    JwtDecoder jwtDecoder() {
        // verifies signature AND expiry; a tampered or expired token never reaches our code
        return NimbusJwtDecoder.withSecretKey(key).build();
    }
}
