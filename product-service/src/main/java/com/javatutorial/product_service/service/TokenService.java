package com.javatutorial.product_service.service;

import com.javatutorial.product_service.model.AppUser;
import com.javatutorial.product_service.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
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
    private final AppUserRepository userRepository;
    private final Duration validity;

    public TokenService(
            JwtEncoder jwtEncoder,
            AppUserRepository userRepository,
            @Value("${security.jwt.validity-minutes}") long validityMinutes
    ) {
        this.jwtEncoder = jwtEncoder;
        this.userRepository = userRepository;
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
        AppUser user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException(authentication.getName()));

        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("product-service")
                .issuedAt(now)
                .expiresAt(now.plus(validity)) // the decoder enforces this on every request
                .subject(String.valueOf(user.getId())) // stable id: never reassigned, unlike a username
                .claim("username", user.getUsername()) // stable id: never reassigned, unlike a username
                .claim("roles", roles)
                .build();

        // remember: claims are base64, NOT encrypted - anyone holding the token can read them
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

}
