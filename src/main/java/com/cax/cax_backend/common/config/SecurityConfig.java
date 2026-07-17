package com.cax.cax_backend.common.config;

import com.cax.cax_backend.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(org.springframework.security.config.Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers(
                                "/health",
                                "/api/public/**",
                                "/api/auth/google",
                                "/api/auth/preview-college",
                                "/api/auth/report-wrong-college",
                                "/api/auth/generate-test-token",
                                "/api/auth/refresh",
                                "/api/admin/auth/google",
                                "/api/admin/auth/dev-login",
                                "/api/auth/qr/**",
                                "/api/auth/2fa/login-verify",
                                "/api/version/**",
                                "/api/colleges",
                                "/api/newsletter/subscribe",
                                "/api/ads/*/click",
                                // Bingo: read-only public endpoints (game info, card view, leaderboard)
                                "/api/games/bingo/public/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        ).permitAll()
                        // Bingo: org-scoped game list is leader-only (role enforced in the
                        // service) — it exposes the full prompt pool, so it requires auth.
                        .requestMatchers("/api/games/bingo/org/**").authenticated()
                        // Bingo player write endpoints require a valid auth token
                        .requestMatchers("/api/games/bingo/player/**").authenticated()
                        // All other endpoints require authentication
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"error\": \"Unauthorized: Session expired or invalid token.\", \"success\": false}");
                        })
                )
                .build();
    }
}
