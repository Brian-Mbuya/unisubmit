package com.unisubmit.security;

import com.unisubmit.domain.User;
import com.unisubmit.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Ends the session of a user who was suspended or deleted AFTER logging in.
 * The login-time check in CustomUserDetailsService only blocks new sessions;
 * without this filter an already-authenticated suspended user kept working
 * until they happened to log out.
 */
@Component
public class SuspensionEnforcementFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    public SuspensionEnforcementFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails details) {
            User fresh = userRepository.findById(details.getUser().getId()).orElse(null);
            if (fresh == null || fresh.isDeleted() || fresh.isSuspended()) {
                SecurityContextHolder.clearContext();
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                response.sendRedirect(request.getContextPath() + "/login?suspended");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/images/")
                || path.equals("/favicon.ico") || path.equals("/favicon.svg")
                || path.equals("/login") || path.equals("/register") || path.equals("/error");
    }
}
