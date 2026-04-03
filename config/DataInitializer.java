package com.chuka.irir.config;

import com.chuka.irir.model.Role;
import com.chuka.irir.model.User;
import com.chuka.irir.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Objects;
import java.util.Set;


/**
 * Data initializer that seeds the database with default users on first startup.
 *
 * Creates a default admin account if no admin users exist in the database.
 * This ensures that the system always has at least one administrator who can
 * manage users and assign roles.
 *
 * <p>
 * <b>IMPORTANT:</b> Change the default admin password after first login in
 * production!
 * </p>
 */
@Configuration
public class DataInitializer {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    /**
     * Seeds a default admin user if none exists.
     * Runs automatically on application startup.
     */
    @Bean
    public CommandLineRunner initializeData(UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap.admin.enabled:false}") boolean bootstrapAdminEnabled,
            @Value("${app.bootstrap.admin.email:}") String bootstrapAdminEmail,
            @Value("${app.bootstrap.admin.password:}") String bootstrapAdminPassword,
            @Value("${app.bootstrap.admin.first-name:System}") String bootstrapAdminFirstName,
            @Value("${app.bootstrap.admin.last-name:Administrator}") String bootstrapAdminLastName,
            @Value("${app.bootstrap.admin.department:Computer Science}") String bootstrapAdminDepartment) {
        return args -> {
            if (!bootstrapAdminEnabled) {
                logger.info("Bootstrap admin seeding is disabled.");
                logUserStatistics(userRepository);
                return;
            }

            String email = normalizeRequired(bootstrapAdminEmail, "Bootstrap admin email is required when seeding is enabled.");
            String password = normalizeRequired(bootstrapAdminPassword, "Bootstrap admin password is required when seeding is enabled.");

            // Only seed if no admin users exist
            if (userRepository.findByRoles(Role.ADMIN).isEmpty()) {
                logger.info("No admin users found — creating default admin account...");

                User admin = User.builder()
                        .firstName(bootstrapAdminFirstName)
                        .lastName(bootstrapAdminLastName)
                        .email(email)
                        .password(passwordEncoder.encode(password))
                        .department(bootstrapAdminDepartment)
                        .roles(Set.of(Role.ADMIN))
                        .enabled(true)
                        .accountNonLocked(true)
                        .build();

                userRepository.save(Objects.requireNonNull(admin));
                logger.info("Bootstrap admin account created: {}", email);
                logger.warn("Change the bootstrap admin password after first login.");
            } else {
                logger.info("Admin account(s) already exist — skipping seed.");
            }

            logUserStatistics(userRepository);
        };
    }

    private void logUserStatistics(UserRepository userRepository) {
        logger.info("=== IRIR User Statistics ===");
        logger.info("  Students:    {}", userRepository.countByRoles(Role.STUDENT));
        logger.info("  Supervisors: {}", userRepository.countByRoles(Role.SUPERVISOR));
        logger.info("  Directorate: {}", userRepository.countByRoles(Role.DIRECTORATE));
        logger.info("  Admins:      {}", userRepository.countByRoles(Role.ADMIN));
        logger.info("  Total:       {}", userRepository.count());
    }

    private String normalizeRequired(String value, String message) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalStateException(message);
        }
        return normalized;
    }
}
