package com.unisubmit.controller.api;

import com.unisubmit.domain.User;
import com.unisubmit.exception.UnauthorizedException;
import com.unisubmit.repository.UserRepository;
import com.unisubmit.security.CustomUserDetails;
import com.unisubmit.security.TooManyLoginAttemptsException;
import com.unisubmit.security.jwt.JwtTokenService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Token endpoint for the Flutter client.
 * <p>
 * <b>This does not implement its own authentication.</b> It delegates to the SAME
 * {@link AuthenticationManager} the browser login uses, which routes through
 * {@code CustomUserDetailsService} — so the mobile client automatically inherits the
 * brute-force throttle (5 failures per username+IP → 15 min), login by admission number
 * or staff ID, soft-delete handling and the suspension check. Hand-rolling a password
 * comparison here would have quietly dropped all four.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthApiController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenService tokenService;
    private final UserRepository userRepository;

    public AuthApiController(AuthenticationManager authenticationManager,
                             JwtTokenService tokenService,
                             UserRepository userRepository) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.userRepository = userRepository;
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    public record LoginResponse(String token, String tokenType, long expiresIn, String role, String displayName) {}

    public record ApiError(String error, String message) {}

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        Authentication auth;
        try {
            auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (AuthenticationException ex) {
            return authFailure(ex);
        }

        CustomUserDetails principal = (CustomUserDetails) auth.getPrincipal();
        return ResponseEntity.ok(new LoginResponse(
                tokenService.issue(principal),
                "Bearer",
                tokenService.expiresInSeconds(),
                principal.getUser().getRole().name(),
                principal.getUser().getName()
        ));
    }

    public record MeResponse(Long id, String username, String name, String role) {}

    /**
     * Validates a stored token and returns the current identity.
     * <p>
     * The Flutter client calls this on launch: a 200 means the cached token is still good
     * and carries the role needed to pick the right home screen; a 401 means "show login".
     * Reading the profile from the DB rather than from token claims means a renamed or
     * role-changed account is reflected immediately instead of at token expiry.
     */
    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(@AuthenticationPrincipal Jwt jwt) {
        Long userId = ((Number) jwt.getClaim("uid")).longValue();
        User user = userRepository.findById(userId).orElseThrow(
                () -> new UnauthorizedException("Account no longer exists."));
        return ResponseEntity.ok(new MeResponse(
                user.getId(), user.getUsername(), user.getName(), user.getRole().name()));
    }

    /**
     * Maps the authentication failure modes onto status codes a mobile client can act on.
     * The cause is unwrapped first because exceptions thrown inside
     * {@code CustomUserDetailsService} arrive wrapped in an
     * {@code InternalAuthenticationServiceException} — the same unwrapping the browser
     * failure handler in {@code SecurityConfig} performs.
     */
    private ResponseEntity<ApiError> authFailure(AuthenticationException ex) {
        Throwable cause = ex;
        if (ex instanceof org.springframework.security.authentication.InternalAuthenticationServiceException
                && ex.getCause() != null) {
            cause = ex.getCause();
        }
        if (cause instanceof TooManyLoginAttemptsException) {
            // 429 so the client can show a countdown instead of "wrong password".
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new ApiError("locked_out", cause.getMessage()));
        }
        if (cause instanceof LockedException) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiError("suspended", cause.getMessage()));
        }
        if (cause instanceof BadCredentialsException) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiError("bad_credentials", "Invalid username or password."));
        }
        // Unknown user included here on purpose: never reveal whether the account exists.
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError("bad_credentials", "Invalid username or password."));
    }
}
