package com.tourlk.config;

import com.tourlk.security.JwtFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless, JWT-based security configuration.
 * - /api/auth/** and swagger endpoints are public.
 * - GET-only browse/detail endpoints for destinations, accommodations,
 *   packages, vehicles and reviews are also public, matching the fact
 *   that those controller methods carry no @PreAuthorize (they're meant
 *   to be visible to anonymous visitors).
 * - Everything else requires a valid JWT.
 * - Method-level access control via @PreAuthorize is still enabled and
 *   remains the authority for role checks (e.g. "/pending-approval" and
 *   "/mine" sub-paths under these same prefixes are wildcard-matched
 *   below too, but @PreAuthorize on those controller methods rejects
 *   anonymous/unauthorized callers with a 403 regardless of the URL-level
 *   permitAll, so nothing admin/role-restricted is actually exposed).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/auth/**",
            "/api/payments/webhook",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**"
    };

    // GET-only public browse/detail routes. Method security (@PreAuthorize)
    // still guards the role-restricted sub-paths that share these prefixes
    // (e.g. GET /api/destinations/all, GET /api/*/pending-approval, GET /api/*/mine).
    private static final String[] PUBLIC_GET_ENDPOINTS = {
            "/api/destinations",
            "/api/destinations/*",
            "/api/accommodations",
            "/api/accommodations/*",
            "/api/packages",
            "/api/packages/*",
            "/api/vehicles",
            "/api/vehicles/*",
            "/api/reviews",
            "/api/reviews/summary"
    };

    private final JwtFilter jwtFilter;
    private final UserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS).permitAll()
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}