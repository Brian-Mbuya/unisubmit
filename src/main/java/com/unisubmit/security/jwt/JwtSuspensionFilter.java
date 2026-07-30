package com.unisubmit.security.jwt;

import com.unisubmit.domain.User;
import com.unisubmit.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * The API-side counterpart to {@code SuspensionEnforcementFilter}.
 * <p>
 * <b>Why a second filter was necessary.</b> {@code SuspensionEnforcementFilter} tests
 * {@code principal instanceof CustomUserDetails}. On the JWT chain the principal is a
 * {@link JwtAuthenticationToken}, so that test never passes and the filter silently
 * no-ops — a student suspended by an admin would keep full API access. It also
 * {@code sendRedirect}s to {@code /login?suspended}, which is meaningless to a Flutter
 * client. This filter closes both gaps: same intent, right principal type, JSON 401.
 * <p>
 * <b>The structural problem this filter exists to paper over.</b> Sessions are server
 * state, so revoking one is instant. A JWT is a self-contained bearer credential — once
 * signed, it is valid until {@code exp} no matter what happens to the account. Every
 * revocation scheme is therefore a trade between staleness and per-request cost; see
 * {@link #shouldRevoke(Long)}.
 */
@Component
public class JwtSuspensionFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    public JwtSuspensionFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Long userId = readUserId(jwtAuth.getToken());
            if (userId == null || shouldRevoke(userId)) {
                SecurityContextHolder.clearContext();
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(
                        "{\"error\":\"account_unavailable\","
                        + "\"message\":\"This account is suspended or no longer exists. Please sign in again.\"}");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    /** The {@code uid} claim is written as a Long by JwtTokenService but round-trips through JSON as a Number. */
    private Long readUserId(Jwt token) {
        Object uid = token.getClaim("uid");
        return (uid instanceof Number n) ? n.longValue() : null;
    }

    /**
     * TODO(owner): decide the revocation policy for the mobile API.
     * <p>
     * The implementation below is the SAFEST option — a fresh {@code findById} on every
     * authenticated API request, mirroring what the browser filter does. It is a correct
     * starting point, but it is not obviously the right one for this deployment, and the
     * choice is yours to make:
     * <ul>
     *   <li><b>Check every request (below).</b> Suspension takes effect immediately.
     *       Costs one SELECT per API call — and CODEBASE-MAP §10.4 notes the connection
     *       pool maxes at 5 with OSIV already holding connections during render. A phone
     *       polling AI-insight status every 3s is a lot of extra round-trips.</li>
     *   <li><b>Trust the token until it expires.</b> Zero DB cost. A suspended student
     *       keeps API access for up to {@code app.api.jwt.access-ttl} (currently 12h).
     *       Shortening the TTL narrows the window but forces re-login more often — bad UX
     *       on a phone with intermittent campus wifi.</li>
     *   <li><b>Cache the verdict per user for N seconds.</b> Bounds both the staleness and
     *       the query rate, at the cost of a cache to reason about. Note the existing
     *       in-memory caches ({@code LoginAttemptService}, sessions) all reset on redeploy
     *       — consistent with how this app already behaves.</li>
     * </ul>
     * Replace the body with whichever you choose. Returning {@code true} revokes.
     */
    private boolean shouldRevoke(Long userId) {
        User fresh = userRepository.findById(userId).orElse(null);
        return fresh == null || fresh.isDeleted() || fresh.isSuspended();
    }
}
