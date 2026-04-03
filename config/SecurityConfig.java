package com.chuka.irir.config;

import com.chuka.irir.service.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Spring Security configuration for the IRIR application.
 *
 * Defines:
 * <ul>
 *   <li>BCrypt password encoder for secure password hashing</li>
 *   <li>URL-based access control rules (RBAC)</li>
 *   <li>Custom login/logout pages and handlers</li>
 *   <li>Session management with configurable timeout</li>
 *   <li>Security headers (XSS protection, content-type sniffing, frame options)</li>
 * </ul>
 *
 * <p>Method-level security is enabled via {@link EnableMethodSecurity},
 * allowing {@code @PreAuthorize} annotations on service/controller methods.</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;

    public SecurityConfig(CustomUserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    // ==================== Password Encoder ====================

    /**
     * BCrypt password encoder bean.
     * Used for hashing passwords during registration and verifying during login.
     * BCrypt automatically handles salting and has a configurable strength factor.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12); // Strength factor 12 (2^12 iterations)
    }

    // ==================== Authentication Provider ====================

    /**
     * Configures the DAO authentication provider with our custom UserDetailsService
     * and BCrypt password encoder.
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider(PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }

    /**
     * Exposes the AuthenticationManager as a bean for programmatic authentication.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    // ==================== Security Filter Chain ====================

    /**
     * Defines the HTTP security filter chain with URL-based access rules,
     * login/logout configuration, and security headers.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   DaoAuthenticationProvider authenticationProvider) throws Exception {
        http
            .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"))
            // Authentication provider
            .authenticationProvider(authenticationProvider)

            // ---- URL-based Access Control (RBAC) ----
            .authorizeHttpRequests(auth -> auth
                // Public pages — accessible without authentication
                .requestMatchers(
                    "/", "/login", "/register", "/forgot-password", "/reset-password",
                    "/css/**", "/js/**", "/images/**",
                    "/webjars/**", "/error/**", "/h2-console/**"
                ).permitAll()

                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/lecturer/**").hasRole("SUPERVISOR")
                .requestMatchers("/api/student/**").hasRole("STUDENT")
                .requestMatchers("/api/academic/**").authenticated()

                // Student-only pages
                .requestMatchers("/student/**").hasRole("STUDENT")

                // Supervisor-only pages
                .requestMatchers("/supervisor/**").hasRole("SUPERVISOR")

                // Directorate-only pages
                .requestMatchers("/directorate/**").hasRole("DIRECTORATE")

                // Admin-only pages
                .requestMatchers("/admin/**").hasRole("ADMIN")

                // All other pages require authentication
                .anyRequest().authenticated()
            )

            // ---- Form Login Configuration ----
            .formLogin(form -> form
                .loginPage("/login")                          // Custom login page
                .loginProcessingUrl("/login")                 // POST endpoint for login form
                .usernameParameter("identifier")              // Use email or staff/student ID as username field
                .defaultSuccessUrl("/dashboard", true)        // Redirect after successful login
                .failureUrl("/login?error=true")              // Redirect after failed login
                .permitAll()
            )

            // ---- Logout Configuration ----
            .logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                .logoutSuccessUrl("/login?logout=true")       // Redirect after logout
                .invalidateHttpSession(true)                  // Clear session data
                .deleteCookies("JSESSIONID")                  // Remove session cookie
                .permitAll()
            )

            // ---- Session Management ----
            .sessionManagement(session -> session
                .maximumSessions(1)                           // One session per user
                .expiredUrl("/login?expired=true")             // Redirect on session expiry
            )

            // ---- Security Headers ----
            .headers(headers -> headers
                .contentSecurityPolicy(csp ->
                    csp.policyDirectives(
                        "default-src 'self'; " +
                        "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; " +
                        "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://fonts.googleapis.com; " +
                        "font-src 'self' https://fonts.gstatic.com https://cdn.jsdelivr.net; " +
                        "img-src 'self' data:;"
                    )
                )
                .frameOptions(frame -> frame.sameOrigin())    // Allow H2 console in dev profile
            );

        return http.build();
    }
}
