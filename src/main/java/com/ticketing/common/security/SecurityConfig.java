package com.ticketing.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketing.user.service.UserDetailsServiceImpl;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final UserDetailsServiceImpl userDetailsService;
    // Fix M-002: only needed to build the RateLimitFilter bean below.
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, RateLimitFilter rateLimitFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())       // X-Frame-Options: DENY — prevents clickjacking
                        .httpStrictTransportSecurity(hsts -> hsts  // HSTS — forces HTTPS in production (HTTPS-only header)
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)))
                // CSP is NOT added here — backend serves JSON + Swagger UI; CSP belongs
                // on the Next.js frontend only (next.config.ts). Adding it here breaks
                // Swagger UI's inline scripts.
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**", "/api/auth/**", "/api/v1/payments/webhook").permitAll()
                        // Fix SECURITY-6: only /actuator/health is public (needed by Docker/Railway
                        // healthchecks) — every other actuator endpoint (env, metrics, beans, etc.)
                        // previously fell into the blanket "/actuator/**".permitAll() above and was
                        // reachable by anyone on the internet. Now ADMIN-only.
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/events", "/api/events/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/search/events").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/venues", "/api/venues/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/categories", "/api/categories/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
                        .anyRequest().authenticated())
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                // Fix M-002: runs AFTER JwtFilter so the per-user booking limit can read the
                // authenticated principal from the SecurityContext.
                .addFilterAfter(rateLimitFilter, JwtFilter.class);

        return http.build();
    }

    /**
     * Fix M-002: a plain {@code @Bean} (never a scanned {@code @Component}) so it is never
     * instantiated inside a {@code @WebMvcTest} slice. Disabled by default — there is no
     * dedicated {@code test} Spring profile in this project (all tests run under {@code local}),
     * so a property flag is the only way to keep every existing test suite unaffected while
     * still enforcing the limit in production ({@code app.rate-limit.enabled=true}).
     */
    @Bean
    public RateLimitFilter rateLimitFilter(
            @Value("${app.rate-limit.enabled:false}") boolean rateLimitEnabled) {
        return new RateLimitFilter(redisTemplate, objectMapper, rateLimitEnabled);
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
