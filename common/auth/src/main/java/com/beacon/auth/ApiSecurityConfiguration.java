package com.beacon.auth;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Standard API security filter chain that enforces JWT authentication for Beacon
 * backend services. Services may opt-in by setting
 * {@code beacon.auth.api-enabled=true}.
 */
@AutoConfiguration(after = AuthAutoConfiguration.class)
@ConditionalOnProperty(prefix = "beacon.auth", name = "api-enabled", havingValue = "true")
public class ApiSecurityConfiguration {

    /**
     * Builds a stateless security filter chain that protects every request apart
     * from actuator health probes.
     *
     * @param http Spring Security HTTP builder
     * @return configured security filter chain
     * @throws Exception when the builder fails to produce a filter chain
     */
    @Bean
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable());
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .anyRequest().authenticated());
        http.oauth2ResourceServer(resource -> resource.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
