package com.tourlk.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JwtUtil}: token round-tripping, expiry, and the
 * failure modes {@code JwtFilter} relies on catching.
 */
class JwtUtilTest {

    // Base64 of a >= 256-bit key, matching the app default / test profile.
    private static final String SECRET = "c2VjcmV0LXBsYWNlaG9sZGVyLWNoYW5nZS1tZS1pbi1sb2NhbC1jb25maWctMjU2Yml0c30=";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, 3_600_000L);
    }

    @Test
    void generateToken_thenExtract_roundTripsEmailAndRole() {
        String token = jwtUtil.generateToken("tourist@example.com", "TOURIST");

        assertThat(jwtUtil.extractEmail(token)).isEqualTo("tourist@example.com");
        assertThat(jwtUtil.extractRole(token)).isEqualTo("TOURIST");
    }

    @Test
    void isTokenValid_freshTokenForSameEmail_returnsTrue() {
        String token = jwtUtil.generateToken("tourist@example.com", "TOURIST");

        assertThat(jwtUtil.isTokenValid(token, "tourist@example.com")).isTrue();
    }

    @Test
    void isTokenValid_emailDoesNotMatch_returnsFalse() {
        String token = jwtUtil.generateToken("tourist@example.com", "TOURIST");

        assertThat(jwtUtil.isTokenValid(token, "someone.else@example.com")).isFalse();
    }

    @Test
    void extractEmail_expiredToken_throwsExpiredJwtException() {
        JwtUtil shortLived = new JwtUtil(SECRET, -1_000L); // already expired on creation
        String token = shortLived.generateToken("tourist@example.com", "TOURIST");

        assertThatThrownBy(() -> jwtUtil.extractEmail(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void extractEmail_tokenSignedWithAnotherKey_throwsJwtException() {
        String foreignToken = Jwts.builder()
                .setSubject("tourist@example.com")
                .claim("role", "TOURIST")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.secretKeyFor(SignatureAlgorithm.HS256))
                .compact();

        assertThatThrownBy(() -> jwtUtil.extractEmail(foreignToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void extractEmail_malformedToken_throwsException() {
        assertThatThrownBy(() -> jwtUtil.extractEmail("not-a-jwt"))
                .isInstanceOf(RuntimeException.class);
    }
}
