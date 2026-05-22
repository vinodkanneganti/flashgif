package com.flashgif.infra.security;

import com.flashgif.developer.security.DeveloperApiKeyFilter;
import com.flashgif.developer.security.DeveloperRateLimitFilter;
import com.flashgif.users.security.UserJwtFilter;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@RequiredArgsConstructor
class SecurityConfig {

    private final UserJwtFilter userJwtFilter;
    private final DeveloperApiKeyFilter developerApiKeyFilter;
    private final DeveloperRateLimitFilter developerRateLimitFilter;

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * CORS for browser-based clients. Dev allows the Next.js dev server; production
     * tightens to the real web origin via {@code FLASHGIF_WEB_ORIGINS} env var
     * (comma-separated list).
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cors = new CorsConfiguration();
        String envOrigins = System.getenv("FLASHGIF_WEB_ORIGINS");
        cors.setAllowedOrigins(envOrigins == null || envOrigins.isBlank()
                ? List.of("http://localhost:3000")
                : List.of(envOrigins.split("\\s*,\\s*")));
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        cors.setExposedHeaders(List.of("Retry-After"));
        cors.setAllowCredentials(true);
        cors.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }

    /**
     * Prevent Spring Boot from auto-registering filters as top-level servlet filters.
     * They run inside their respective security chains via {@code addFilterBefore};
     * double-registration (@Component Filter + addFilterBefore) causes surprising
     * ordering, especially with CSRF / authz interactions.
     */
    @Bean
    FilterRegistrationBean<UserJwtFilter> disableUserJwtFilterAutoRegistration(UserJwtFilter f) {
        return disabled(f);
    }
    @Bean
    FilterRegistrationBean<DeveloperApiKeyFilter> disableDevKeyFilterAutoRegistration(DeveloperApiKeyFilter f) {
        return disabled(f);
    }
    @Bean
    FilterRegistrationBean<DeveloperRateLimitFilter> disableDevRateLimitFilterAutoRegistration(DeveloperRateLimitFilter f) {
        return disabled(f);
    }
    private static <T extends jakarta.servlet.Filter> FilterRegistrationBean<T> disabled(T filter) {
        FilterRegistrationBean<T> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }

    /**
     * Developer chain — third-party API-key auth + per-key rate limit.
     * Filters: key auth → bucket check (429 + Retry-After) → record usage.
     */
    @Bean
    @Order(1)
    SecurityFilterChain developerChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/v1/developer/**")
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(developerApiKeyFilter,    AuthorizationFilter.class)
                .addFilterBefore(developerRateLimitFilter, AuthorizationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .anyRequest().authenticated());
        return http.build();
    }

    /**
     * User-facing chain: stateless JWT auth via {@link UserJwtFilter}.
     * Permits public read traffic; everything else requires a valid Bearer token.
     */
    @Bean
    @Order(2)
    SecurityFilterChain userChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .anonymous(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(userJwtFilter, AuthorizationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // --- Always permit internal ERROR dispatches so Spring Boot's
                        //     /error machinery can render real status/body. Without this,
                        //     any 4xx/5xx from a permitAll endpoint gets masked as 401. ---
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        // --- Open: ops + docs ---
                        // NB: "/v3/api-docs/**" matches subpaths only — `.yaml` and the
                        // bare path need explicit entries.
                        .requestMatchers(
                                "/actuator/health", "/actuator/info",
                                "/v3/api-docs", "/v3/api-docs/**",
                                "/v3/api-docs.yaml", "/v3/api-docs.json",
                                "/swagger-ui/**", "/swagger-ui.html"
                        ).permitAll()
                        // --- Open: auth endpoints themselves ---
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // --- Authed: anything under /users/me must come first
                        //     so it wins over the broader /users/*/... permitAll below. ---
                        .requestMatchers("/api/v1/users/me/**").authenticated()
                        // --- Open: public read traffic ---
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/search/**",
                                "/api/v1/trending",
                                "/api/v1/channels/*",
                                "/api/v1/channels/*/media",
                                "/api/v1/users/*/collections",
                                "/api/v1/collections/*",
                                "/api/v1/collections/*/items"
                        ).permitAll()
                        // --- Everything else needs a token ---
                        .anyRequest().authenticated()
                );
        return http.build();
    }
}
