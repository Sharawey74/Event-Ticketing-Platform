package com.ticketing.common.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Shared test security configuration for all @WebMvcTest slices.
 *
 * Replaces the real SecurityConfig (which requires JwtFilter + UserDetailsServiceImpl
 * with DB dependencies) with a self-contained, in-memory security setup.
 *
 * Key properties:
 *  - @EnableMethodSecurity ensures @PreAuthorize AOP interceptors ARE active
 *  - Filters remain ENABLED (no addFilters=false) so security is real
 *  - Two test principals: ADMIN and ORGANIZER, usable via @WithMockUser
 *  - No JWT, no database, no external dependencies
 *
 * Picked up by @WebMvcTest slices via @Import(TestSecurityConfig.class).
 */
@TestConfiguration
@EnableMethodSecurity
public class TestSecurityConfig {

    @Bean
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(
                    new org.springframework.security.web.authentication.HttpStatusEntryPoint(
                        org.springframework.http.HttpStatus.UNAUTHORIZED)))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET,
                    "/api/events", "/api/events/**",
                    "/api/search/events",
                    "/api/venues", "/api/venues/**",
                    "/api/categories", "/api/categories/**").permitAll()
                .anyRequest().authenticated());

        return http.build();
    }

    @Bean
    public UserDetailsService testUserDetailsService() {
        var admin = User.builder()
            .username("admin")
            .password(passwordEncoder().encode("password"))
            .roles("ADMIN")
            .build();

        var organizer = User.builder()
            .username("organizer")
            .password(passwordEncoder().encode("password"))
            .roles("ORGANIZER")
            .build();

        return new InMemoryUserDetailsManager(admin, organizer);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
