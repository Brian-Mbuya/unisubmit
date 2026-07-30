package com.unisubmit.security.jwt;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * Stateless JWT chain for the Flutter client — scoped to {@code /api/v1/**} ONLY.
 * <p>
 * <b>How the two chains coexist.</b> Spring Security evaluates filter chains in
 * {@code @Order} and uses the FIRST whose {@code securityMatcher} matches the request.
 * This chain is {@code @Order(1)} and matches only {@code /api/v1/**}; the original
 * {@code SecurityConfig} chain is {@code @Order(2)} with no matcher, so it still catches
 * every Thymeleaf route with the session cookie + formLogin exactly as before. Nothing
 * about the browser app changes.
 * <p>
 * <b>Why {@code /api/v1/**} and not {@code /api/**}.</b> The pre-existing
 * {@code /api/academic/**} (anonymous, register-form cascading selects),
 * {@code /api/users/**} (member picker) and {@code /api/ai/**} endpoints are called by
 * JavaScript running inside already-authenticated browser PAGES — they rely on the
 * session cookie and CSRF token. Matching {@code /api/**} here would strip their session
 * and break them. The version prefix is the boundary between "browser helpers" and
 * "mobile API".
 * <p>
 * <b>Why CSRF is disabled here and nowhere else.</b> CSRF attacks work because browsers
 * attach cookies to cross-site requests automatically. This chain is STATELESS and
 * authenticates from the {@code Authorization: Bearer} header, which no browser attaches
 * on its own — so there is nothing for an attacker to ride. The session chain keeps CSRF
 * on, and the {@code <meta>} + hidden-input contract in the templates is untouched.
 */
@Configuration
@Order(1)
public class ApiSecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(ApiSecurityConfig.class);

    /**
     * HMAC signing key. Configure {@code API_JWT_SECRET} (32+ chars) in the environment.
     * <p>
     * If unset we generate a random key at boot and warn. That degrades to the SAME
     * behaviour the browser app already has — in-memory sessions mean a redeploy logs
     * everyone out (CODEBASE-MAP §7) — so an unconfigured deployment is inconvenient,
     * never insecure. It also rules out the classic accident of a hard-coded default
     * secret shipping to production.
     */
    @Bean
    public SecretKey apiJwtSecretKey(@Value("${app.api.jwt.secret:}") String configured) {
        if (configured != null && configured.length() >= 32) {
            return new SecretKeySpec(configured.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        }
        if (configured != null && !configured.isBlank()) {
            log.warn("API_JWT_SECRET is set but shorter than 32 chars — too weak for HS256. Generating an ephemeral key instead.");
        } else {
            log.warn("API_JWT_SECRET is not set — generating an ephemeral signing key. "
                    + "All mobile clients will be logged out on every redeploy. Set API_JWT_SECRET to fix.");
        }
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        return new SecretKeySpec(random, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey apiJwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(apiJwtSecretKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey apiJwtSecretKey) {
        return NimbusJwtDecoder.withSecretKey(apiJwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    /**
     * Maps the token's {@code role} claim ("STUDENT") onto a {@code ROLE_STUDENT}
     * authority, so {@code hasRole(...)} and every existing {@code @PreAuthorize}
     * behave identically whether the caller arrived by cookie or by bearer token.
     */
    @Bean
    public JwtAuthenticationConverter apiJwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    /**
     * Stops Spring Boot from ALSO registering {@link JwtSuspensionFilter} as a plain
     * servlet filter. Boot auto-registers every {@code Filter} bean it finds; because we
     * additionally wire this one into the security chain via {@code addFilterAfter}, it
     * would otherwise run twice per request — once outside the chain (where the
     * SecurityContext is not yet populated, so it silently does nothing) and once inside.
     * Harmless but wasteful, and confusing to debug.
     */
    @Bean
    public FilterRegistrationBean<JwtSuspensionFilter> disableJwtSuspensionAutoRegistration(
            JwtSuspensionFilter filter) {
        FilterRegistrationBean<JwtSuspensionFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain apiFilterChain(HttpSecurity http,
                                              JwtAuthenticationConverter apiJwtAuthenticationConverter,
                                              JwtSuspensionFilter jwtSuspensionFilter) throws Exception {
        http
            .securityMatcher("/api/v1/**")
            // Safe here and ONLY here — see the class javadoc.
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/login").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/lecturer/**").hasRole("LECTURER")
                .requestMatchers("/api/v1/student/**").hasRole("STUDENT")
                .anyRequest().authenticated()
            )
            // Default entry point is BearerTokenAuthenticationEntryPoint: a bad/absent
            // token gets 401 + WWW-Authenticate, never a 302 to the HTML login page.
            .oauth2ResourceServer(oauth -> oauth
                .jwt(jwt -> jwt.jwtAuthenticationConverter(apiJwtAuthenticationConverter))
            )
            // Runs once the bearer token has been validated and the principal is set.
            .addFilterAfter(jwtSuspensionFilter, BearerTokenAuthenticationFilter.class);

        return http.build();
    }
}
