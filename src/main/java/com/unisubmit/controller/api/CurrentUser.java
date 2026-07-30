package com.unisubmit.controller.api;

import com.unisubmit.domain.User;
import com.unisubmit.exception.UnauthorizedException;
import com.unisubmit.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Resolves the authenticated API caller to a {@link User}.
 * <p>
 * The JWT principal carries only the {@code uid} claim — deliberately, so a renamed or
 * demoted account is never served from stale token data (see {@code JwtTokenService}).
 * That means every API handler needs the same "claim → entity" hop, and this is it,
 * in one place rather than repeated per controller.
 */
@Component
public class CurrentUser {

    private final UserRepository userRepository;

    public CurrentUser(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * @throws UnauthorizedException if there is no bearer token, or the account behind it
     *         has since been deleted — mapped to 403 by {@code ApiExceptionHandler}.
     */
    public User require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            throw new UnauthorizedException("Not authenticated.");
        }
        Object uid = jwtAuth.getToken().getClaim("uid");
        if (!(uid instanceof Number n)) {
            throw new UnauthorizedException("Malformed token.");
        }
        return userRepository.findById(n.longValue())
                .orElseThrow(() -> new UnauthorizedException("Account no longer exists."));
    }
}
