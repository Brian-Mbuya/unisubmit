package com.chuka.irir.service;

import com.chuka.irir.model.User;
import com.chuka.irir.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Custom implementation of Spring Security's {@link UserDetailsService}.
 *
 * Loads user details from the database by email address or staff/student identifier for authentication.
 * Maps IRIR {@link com.chuka.irir.model.Role} enums to Spring Security
 * {@link GrantedAuthority} objects with the "ROLE_" prefix.
 *
 * <p>This service is used by Spring Security's authentication manager
 * during the login process.</p>
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private static final Logger logger = LoggerFactory.getLogger(CustomUserDetailsService.class);

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Loads a user by email address or registration/staff identifier for Spring Security authentication.
     *
     * @param identifier the email address or staff/student identifier entered in the login form
     * @return a Spring Security {@link UserDetails} object
     * @throws UsernameNotFoundException if no user exists with the given identifier
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        if (identifier == null || identifier.isBlank()) {
            throw new UsernameNotFoundException("An email address or staff/student identifier is required.");
        }
        String normalizedIdentifier = identifier.trim();
        logger.debug("Attempting to authenticate user: {}", normalizedIdentifier);

        User user = userRepository.findByEmail(normalizedIdentifier)
                .or(() -> userRepository.findByRegNumber(normalizedIdentifier))
                .orElseThrow(() -> {
                    logger.warn("Authentication failed — user not found: {}", normalizedIdentifier);
                    return new UsernameNotFoundException("User not found with identifier: " + normalizedIdentifier);
                });

        // Map IRIR roles to Spring Security granted authorities
        Set<com.chuka.irir.model.Role> roles = user.getRoles() == null ? Set.of() : user.getRoles();
        Collection<? extends GrantedAuthority> authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .collect(Collectors.toSet());

        logger.debug("User '{}' authenticated successfully with roles: {}", user.getEmail(), authorities);

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                user.isEnabled(),
                true,                    // accountNonExpired
                true,                    // credentialsNonExpired
                user.isAccountNonLocked(),
                authorities
        );
    }
}
