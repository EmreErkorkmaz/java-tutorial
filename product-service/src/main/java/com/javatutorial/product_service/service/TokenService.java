package com.javatutorial.product_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Collectors;

@Service
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final Duration validity;

    public TokenService(
            JwtEncoder jwtEncoder,
            @Value("${security.jwt.validity-minutes}") long validityMinutes
    ) {
        this.jwtEncoder = jwtEncoder;
        this.validity = Duration.ofMinutes(validityMinutes);
    }

    public String issue(Authentication authentication) {
        Instant now = Instant.now();
        // Space-separated, because that is the convention Spring's default authorities
        // converter expects when it reads a claim back out of a token.
        String roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .collect(Collectors.joining(" "));

        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("product-service")
                .issuedAt(now)
                .expiresAt(now.plus(validity)) // the decoder enforces this on every request
                .subject(authentication.getName())
                .claim("roles", roles)
                .build();

        // remember: claims are base64, NOT encrypted - anyone holding the token can read them
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

}
