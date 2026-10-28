package com.eexchange.backend.Configuration;

import com.eexchange.backend.DTO.ApiResponse;
import com.eexchange.backend.Configuration.Filters.AuthRateLimitFilter;
import com.eexchange.backend.Configuration.Filters.JWTfilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Configuration
public class SecurityConfig {

    private final JWTfilter jwtFilter;
    private final AuthRateLimitFilter authRateLimitFilter;
    private final String allowedOrigins;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JWTfilter jwtFilter,
                          AuthRateLimitFilter authRateLimitFilter,
                          @Value("${app.cors.allowed-origins}") String allowedOrigins,
                          ObjectMapper objectMapper) {
        this.jwtFilter = jwtFilter;
        this.authRateLimitFilter = authRateLimitFilter;
        this.allowedOrigins = allowedOrigins;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/api/admin/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/colleges", "/api/categories").permitAll()
                        .requestMatchers("/api/admin/**")
                        .hasAnyAuthority("ROLE_SUPER_ADMIN", "ROLE_MODERATOR")
                        .anyRequest().authenticated()
                )
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(this::handleUnauthorized)
                        .accessDeniedHandler(this::handleForbidden));

        // Filter chain execution sequence:
        // 1. AuthRateLimitFilter (Rate limiting on auth routes)
        // 2. JWTfilter (Token & Redis status verification)
        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(authRateLimitFilter, JWTfilter.class);

        return http.build();
    }

    private void handleUnauthorized(HttpServletRequest request,
                                    HttpServletResponse response,
                                    AuthenticationException exception) throws IOException {
        response.setHeader("WWW-Authenticate", "Bearer");
        writeError(response, HttpStatus.UNAUTHORIZED, "Authentication required");
    }

    private void handleForbidden(HttpServletRequest request,
                                 HttpServletResponse response,
                                 AccessDeniedException exception) throws IOException {
        writeError(response, HttpStatus.FORBIDDEN, "Access denied");
    }

    private void writeError(HttpServletResponse response,
                            HttpStatus status,
                            String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(message));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(
                Arrays.stream(allowedOrigins.split(","))
                        .map(String::trim)
                        .filter(origin -> !origin.isEmpty())
                        .map(origin -> origin.endsWith("/")
                                ? origin.substring(0, origin.length() - 1)
                                : origin)
                        .filter(origin -> !origin.isEmpty())
                        .toList()
        );

        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        );
        configuration.setAllowedHeaders(
                List.of("Authorization", "Content-Type", "X-Request-ID")
        );
        configuration.setExposedHeaders(List.of("X-Request-ID"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
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
