package com.tourlk.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Shared {@code @WebMvcTest} security setup: a permissive filter chain with
 * method security enabled, so only {@code @PreAuthorize} decides access.
 * Pair with {@code @WithMockUser(roles = ...)} and exclude {@code JwtFilter}
 * from the slice (it needs beans the web slice doesn't load).
 */
@TestConfiguration
@EnableMethodSecurity
public class MethodSecurityTestConfig {

    @Bean
    SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
