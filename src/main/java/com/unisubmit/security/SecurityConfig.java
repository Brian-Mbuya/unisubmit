package com.unisubmit.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico", "/favicon.svg",
                        // PWA assets — must be anonymous: the service worker registers on the
                        // login page and precaches these; a 302→login here breaks installability.
                        "/sw.js", "/manifest.webmanifest", "/icons/**", "/fonts/**", "/offline.html", "/.well-known/assetlinks.json",
                        "/login", "/register", "/forgot-password", "/about", "/error", "/health").permitAll()
                // H2 console: only allowed in the local dev profile (blocked in production)
                .requestMatchers("/h2-console", "/h2-console/**")
                    .access(new org.springframework.security.web.access.expression.WebExpressionAuthorizationManager(
                            "hasIpAddress('127.0.0.1') or hasIpAddress('::1')"))
                // Academic reference data — readable by anonymous users (registration form)
                .requestMatchers("/api/academic/**").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/lecturer/**").hasRole("LECTURER")
                .requestMatchers("/student/**").hasRole("STUDENT")
                // Notification bell — visible to all roles
                .requestMatchers("/notifications", "/notifications/**").authenticated()
                // Group management — students and admins
                .requestMatchers("/groups", "/groups/**").hasAnyRole("STUDENT", "ADMIN")
                // User search API — any authenticated user
                .requestMatchers("/api/users/**").authenticated()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/", true)
                .failureHandler(authenticationFailureHandler())
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            )
            .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()));

        return http.build();
    }

    /**
     * Routes authentication failures to the right login-page message.
     * <p>
     * Exceptions thrown inside {@code CustomUserDetailsService} arrive wrapped
     * in an {@link org.springframework.security.authentication.InternalAuthenticationServiceException},
     * so the real cause is unwrapped first. A {@link LockedException} (suspended
     * account) redirects to /login?suspended with the reason in the session; a
     * {@link TooManyLoginAttemptsException} (brute-force throttle — see
     * {@link LoginAttemptService}: 5 failures per username+IP locks the pair for
     * 15 minutes) redirects to /login?locked; everything else is the generic error.
     */
    @Bean
    public AuthenticationFailureHandler authenticationFailureHandler() {
        return (request, response, exception) -> {
            Throwable cause = exception;
            if (exception instanceof org.springframework.security.authentication.InternalAuthenticationServiceException
                    && exception.getCause() != null) {
                cause = exception.getCause();
            }
            if (cause instanceof TooManyLoginAttemptsException) {
                request.getSession().setAttribute("lockoutMessage", cause.getMessage());
                response.sendRedirect(request.getContextPath() + "/login?locked");
            } else if (cause instanceof LockedException) {
                request.getSession().setAttribute("suspendedReason", cause.getMessage());
                response.sendRedirect(request.getContextPath() + "/login?suspended");
            } else {
                response.sendRedirect(request.getContextPath() + "/login?error");
            }
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
