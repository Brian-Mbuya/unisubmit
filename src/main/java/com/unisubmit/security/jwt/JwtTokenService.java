package com.unisubmit.security.jwt;

import com.unisubmit.security.CustomUserDetails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Issues the signed access tokens consumed by the Flutter client on {@code /api/v1/**}.
 * <p>
 * Browser traffic never touches this class — Thymeleaf pages authenticate with the
 * session cookie issued by {@code SecurityConfig}'s formLogin. See {@link ApiSecurityConfig}
 * for how the two chains are kept apart.
 * <p>
 * <b>Claims are deliberately minimal.</b> Only {@code sub} (username), {@code uid} (the
 * User id) and {@code role} travel in the token. A JWT is signed, not encrypted — anyone
 * holding it can read the payload — so nothing beyond routing/authorisation data belongs
 * in there. The API resolves the full {@code User} from {@code uid} when it needs one,
 * which also means a profile edit takes effect immediately instead of being frozen into
 * a token until expiry.
 */
@Service
public class JwtTokenService {

    private final JwtEncoder encoder;
    private final Duration accessTokenTtl;

    public JwtTokenService(JwtEncoder encoder,
                           @Value("${app.api.jwt.access-ttl:PT12H}") Duration accessTokenTtl) {
        this.encoder = encoder;
        this.accessTokenTtl = accessTokenTtl;
    }

    /**
     * Mints an access token for a user who has ALREADY been authenticated
     * (password verified, suspension checked) by the normal AuthenticationManager.
     * This method does not authenticate anybody — it only serialises the result.
     */
    public String issue(CustomUserDetails principal) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("unisubmit")
                .issuedAt(now)
                .expiresAt(now.plus(accessTokenTtl))
                .subject(principal.getUsername())
                .claim("uid", principal.getUser().getId())
                // Single space-free string; JwtGrantedAuthoritiesConverter in
                // ApiSecurityConfig prefixes it with ROLE_ so hasRole() keeps working
                // exactly as it does on the session chain.
                .claim("role", principal.getUser().getRole().name())
                .build();
        // The JwsHeader MUST be passed explicitly. With claims only, NimbusJwtEncoder
        // defaults to RS256 (asymmetric), then fails to find a matching key in our
        // symmetric ImmutableSecret JWK source — "Failed to select a JWK signing key".
        // The algorithm here has to agree with the decoder's macAlgorithm in ApiSecurityConfig.
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** Seconds until a freshly issued token expires — returned to the client so it can pre-emptively re-login. */
    public long expiresInSeconds() {
        return accessTokenTtl.toSeconds();
    }
}
