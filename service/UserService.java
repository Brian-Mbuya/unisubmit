package com.chuka.irir.service;

import com.chuka.irir.dto.AdminUserCreateDto;
import com.chuka.irir.dto.UserRegistrationDto;
import com.chuka.irir.exception.ResourceNotFoundException;
import com.chuka.irir.model.AuditLog;
import com.chuka.irir.model.Role;
import com.chuka.irir.model.User;
import com.chuka.irir.repository.AuditLogRepository;
import com.chuka.irir.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Service layer for user management operations.
 *
 * Handles user registration, profile updates, role assignment,
 * and account management. All password operations use BCrypt hashing.
 *
 * <p>Integrates with {@link AuditLogRepository} to record significant
 * user-related actions for the admin audit trail.</p>
 */
@Service
@Transactional
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       AuditLogRepository auditLogRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // ==================== Registration ====================

    /**
     * Registers a new user with the STUDENT role.
     *
     * @param dto the registration form data (validated by controller)
     * @return the newly created User entity
     * @throws IllegalArgumentException if email or studentId is already taken
     */
    public User registerStudent(UserRegistrationDto dto) {
        validateRegistration(dto);

        User user = User.builder()
                .firstName(normalizeRequired(dto.getFirstName(), "First name is required"))
                .lastName(normalizeRequired(dto.getLastName(), "Last name is required"))
                .email(normalizeRequired(dto.getEmail(), "Email is required"))
                .regNumber(normalizeOptional(dto.getStudentId()))
                .password(passwordEncoder.encode(dto.getPassword())) // BCrypt hash
                .roles(new HashSet<>(Set.of(Role.STUDENT)))
                .enabled(true)
                .accountNonLocked(true)
                .build();

        User savedUser = userRepository.save(user);

        // Record audit event
        logAudit(savedUser, "USER_REGISTERED", "New student registered: " + savedUser.getEmail());

        logger.info("New student registered: {} ({})", savedUser.getFullName(), savedUser.getEmail());
        return savedUser;
    }

    // ==================== Queries ====================

    /**
     * Finds a user by ID.
     *
     * @param id the user ID
     * @return the User entity
     * @throws ResourceNotFoundException if no user exists with the given ID
     */
    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    /**
     * Finds a user by email address.
     *
     * @param email the email address
     * @return an Optional containing the User, or empty if not found
     */
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * Finds all users with a specific role.
     *
     * @param role the role to filter by
     * @return list of users with the given role
     */
    @Transactional(readOnly = true)
    public List<User> findByRole(Role role) {
        return userRepository.findByRoles(role);
    }

    /**
     * Returns all users in the system. Used by admin panel.
     *
     * @return list of all users
     */
    @Transactional(readOnly = true)
    public List<User> findAll() {
        return userRepository.findAll();
    }

    // ==================== User Management (Admin) ====================

    /**
     * Updates a user's roles. Only accessible by ADMIN users.
     *
     * @param userId the ID of the user to update
     * @param roles  the new set of roles
     * @param admin  the admin user performing the action
     * @return the updated User entity
     */
    public User updateUserRoles(Long userId, Set<Role> roles, User admin) {
        User user = findById(userId);
        Set<Role> oldRoles = new HashSet<>(Objects.requireNonNullElse(user.getRoles(), Set.of()));
        user.setRoles(roles == null ? new HashSet<>() : new HashSet<>(roles));
        User updatedUser = userRepository.save(user);

        logAudit(admin, "USER_ROLE_CHANGED",
                String.format("Roles changed for %s: %s → %s", user.getEmail(), oldRoles, roles));

        logger.info("Admin {} changed roles for {}: {} → {}", admin.getEmail(), user.getEmail(), oldRoles, roles);
        return updatedUser;
    }

    /**
     * Enables or disables a user account. Only accessible by ADMIN users.
     *
     * @param userId  the ID of the user to update
     * @param enabled true to enable, false to disable
     * @param admin   the admin user performing the action
     * @return the updated User entity
     */
    public User setAccountEnabled(Long userId, boolean enabled, User admin) {
        User user = findById(userId);
        if (admin != null && Objects.equals(user.getId(), admin.getId()) && !enabled) {
            throw new IllegalArgumentException("You cannot disable your own admin account.");
        }
        user.setEnabled(enabled);
        User updatedUser = userRepository.save(user);

        String action = enabled ? "USER_ENABLED" : "USER_DISABLED";
        logAudit(admin, action, "Account " + action.toLowerCase().replace("user_", "") + ": " + user.getEmail());

        logger.info("Admin {} {} account: {}", admin.getEmail(), action, user.getEmail());
        return updatedUser;
    }

    /**
     * Creates a managed user account from the admin panel.
     */
    public User createManagedUser(AdminUserCreateDto dto, User admin) {
        Objects.requireNonNull(dto, "Managed user details are required.");
        Objects.requireNonNull(admin, "Admin user is required.");

        String email = normalizeRequired(dto.getEmail(), "Email is required");
        String password = normalizeRequired(dto.getPassword(), "Password is required");
        String confirmPassword = normalizeRequired(dto.getConfirmPassword(), "Password confirmation is required");
        String regNumber = normalizeOptional(dto.getRegNumber());
        Set<Role> roles = dto.getRoles() == null ? Set.of() : new HashSet<>(dto.getRoles());

        if (roles.isEmpty()) {
            throw new IllegalArgumentException("Select at least one role.");
        }
        if (!password.equals(confirmPassword)) {
            throw new IllegalArgumentException("Passwords do not match.");
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email address is already registered: " + email);
        }
        if (regNumber != null && userRepository.existsByRegNumber(regNumber)) {
            throw new IllegalArgumentException("Staff ID is already registered: " + regNumber);
        }

        User user = User.builder()
                .firstName(normalizeRequired(dto.getFirstName(), "First name is required"))
                .lastName(normalizeRequired(dto.getLastName(), "Last name is required"))
                .email(email)
                .regNumber(regNumber)
                .department(normalizeOptional(dto.getDepartment()) != null ? dto.getDepartment().trim() : "Computer Science")
                .password(passwordEncoder.encode(password))
                .roles(roles)
                .enabled(true)
                .accountNonLocked(true)
                .build();

        User savedUser = userRepository.save(user);
        logAudit(admin, "USER_CREATED_BY_ADMIN",
                "Admin created account for " + savedUser.getEmail() + " with roles " + savedUser.getRoles());
        logger.info("Admin {} created account for {} with roles {}", admin.getEmail(), savedUser.getEmail(), savedUser.getRoles());
        return savedUser;
    }

    // ==================== Validation Helpers ====================

    /**
     * Validates registration data before creating a new user.
     *
     * @param dto the registration form data
     * @throws IllegalArgumentException if validation fails
     */
    private void validateRegistration(UserRegistrationDto dto) {
        Objects.requireNonNull(dto, "Registration details are required.");

        String email = normalizeRequired(dto.getEmail(), "Email is required");
        String studentId = normalizeOptional(dto.getStudentId());
        String password = normalizeRequired(dto.getPassword(), "Password is required");
        String confirmPassword = normalizeRequired(dto.getConfirmPassword(), "Password confirmation is required");

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email address is already registered: " + email);
        }

        if (studentId != null && userRepository.existsByRegNumber(studentId)) {
            throw new IllegalArgumentException("Student ID is already registered: " + studentId);
        }

        if (!password.equals(confirmPassword)) {
            throw new IllegalArgumentException("Passwords do not match");
        }
    }

    // ==================== Audit Logging ====================

    /**
     * Records an audit log entry for a user action.
     *
     * @param user    the user who performed the action
     * @param action  the action type (e.g., "USER_REGISTERED")
     * @param details additional context about the action
     */
    private void logAudit(User user, String action, String details) {
        AuditLog log = AuditLog.builder()
                .user(user)
                .action(action)
                .details(details)
                .entityType("User")
                .entityId(user == null ? null : user.getId())
                .build();
        auditLogRepository.save(log);
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
