package com.tourlk.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.function.Function;

/**
 * Handles JWT creation, parsing and validation.
 * Secret and expiration are configurable via app.jwt.* (see application.yml).
 */
@Component
public class JwtUtil {

    private final Key signingKey;
    private final long expirationMs;

    public JwtUtil(@Value("${app.jwt.secret}") String base64Secret,
                    @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.signingKey = Keys.hmacShaKeyFor(java.util.Base64.getDecoder().decode(base64Secret));
        this.expirationMs = expirationMs;
    }

    public String generateToken(String subjectEmail, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .setSubject(subjectEmail)
                .claim("role", role)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    public boolean isTokenValid(String token, String expectedEmail) {
        String email = extractEmail(token);
        return email.equals(expectedEmail) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
        return resolver.apply(claims);
    }

}
