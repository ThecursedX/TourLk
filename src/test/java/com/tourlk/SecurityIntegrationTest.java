package com.tourlk;

import com.jayway.jsonpath.JsonPath;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end security + auth tests against the real filter chain and a real
 * (H2) database: the public-endpoint allowlist, JWT enforcement on protected
 * endpoints, and the register / login flows.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityIntegrationTest {

    private static final String PROTECTED_ENDPOINT = "/api/bookings/mine";

    @Autowired
    private MockMvc mvc;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    // ------------------------------------------------------------------
    // Public endpoints — reachable without a token
    // ------------------------------------------------------------------

    @Test
    void publicEndpoint_authRegister_reachableWithoutToken() throws Exception {
        mvc.perform(register("public-check@example.com", "TOURIST"))
                .andExpect(status().isCreated());
    }

    @Test
    void publicEndpoint_stripeWebhook_reachableWithoutTokenButFailsSignature() throws Exception {
        // No JWT is sent (Stripe can't) — the endpoint is permitAll, so the
        // request reaches the controller and is rejected on signature (400),
        // never on authentication (401/403).
        mvc.perform(post("/api/payments/webhook")
                        .header("Stripe-Signature", "t=1,v1=nope")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publicEndpoint_openApiDocs_reachableWithoutToken() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // Protected endpoints — JWT enforcement
    // ------------------------------------------------------------------

    @Test
    void protectedEndpoint_noToken_isForbidden() throws Exception {
        mvc.perform(get(PROTECTED_ENDPOINT))
                .andExpect(status().isForbidden());
    }

    @Test
    void protectedEndpoint_malformedToken_isForbidden() throws Exception {
        mvc.perform(get(PROTECTED_ENDPOINT).header("Authorization", "Bearer not-a-real-jwt"))
                .andExpect(status().isForbidden());
    }

    @Test
    void protectedEndpoint_expiredToken_isForbidden() throws Exception {
        String expired = Jwts.builder()
                .setSubject("expired@example.com")
                .claim("role", "TOURIST")
                .setIssuedAt(new Date(System.currentTimeMillis() - 120_000))
                .setExpiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(jwtSecret)), SignatureAlgorithm.HS256)
                .compact();

        mvc.perform(get(PROTECTED_ENDPOINT).header("Authorization", "Bearer " + expired))
                .andExpect(status().isForbidden());
    }

    @Test
    void protectedEndpoint_validToken_isAuthorized() throws Exception {
        String token = registerAndGetToken("valid-token@example.com", "TOURIST");

        mvc.perform(get(PROTECTED_ENDPOINT).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpoint_validTokenWrongRole_isForbidden() throws Exception {
        // /api/bookings/mine is @PreAuthorize("hasRole('TOURIST')")
        String guideToken = registerAndGetToken("guide-token@example.com", "GUIDE");

        mvc.perform(get(PROTECTED_ENDPOINT).header("Authorization", "Bearer " + guideToken))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // Auth flows
    // ------------------------------------------------------------------

    @Test
    void register_validRequest_returnsCreatedWithBearerToken() throws Exception {
        mvc.perform(register("new-user@example.com", "TOURIST"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.email").value("new-user@example.com"))
                .andExpect(jsonPath("$.role").value("TOURIST"));
    }

    @Test
    void register_duplicateEmail_returnsBadRequest() throws Exception {
        mvc.perform(register("dupe@example.com", "TOURIST")).andExpect(status().isCreated());

        mvc.perform(register("dupe@example.com", "TOURIST"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_correctPassword_returnsToken() throws Exception {
        mvc.perform(register("login-ok@example.com", "TOURIST")).andExpect(status().isCreated());

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"login-ok@example.com","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void login_wrongPassword_returnsUnauthorized() throws Exception {
        mvc.perform(register("login-bad@example.com", "TOURIST")).andExpect(status().isCreated());

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"login-bad@example.com","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_unknownEmail_returnsUnauthorized() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nobody@example.com","password":"password123"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder register(String email,
                                                                                                String role) {
        return post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Test User","email":"%s","password":"password123","role":"%s"}
                        """.formatted(email, role));
    }

    private String registerAndGetToken(String email, String role) throws Exception {
        String body = mvc.perform(register(email, role))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }
}
