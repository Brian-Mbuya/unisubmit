package com.unisubmit;

import com.unisubmit.domain.User;
import com.unisubmit.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression guard for the seeded-credentials hole: {@code seedData} used to create
 * admin/lecturer/student with a hardcoded {@code password123} in EVERY environment, so
 * any real deployment shipped with a working {@code admin}/{@code password123} login.
 * <p>
 * The default test profile does not enable {@code unisubmit.seed.demo-accounts}, so this
 * class runs in the same configuration production does — if someone makes the demo trio
 * unconditional again, or reintroduces a hardcoded default, these fail.
 */
@SpringBootTest
class AccountBootstrapSecurityTest {

    private static final String WELL_KNOWN_PASSWORD = "password123";

    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void adminDoesNotUseTheWellKnownDemoPassword() {
        User admin = userRepository.findByUsername("admin").orElseThrow(
                () -> new AssertionError("Expected an 'admin' account to be bootstrapped."));

        assertFalse(passwordEncoder.matches(WELL_KNOWN_PASSWORD, admin.getPassword()),
                "The bootstrapped admin must never be reachable with the well-known demo "
                        + "password outside the demo profile.");
    }

    @Test
    void demoLecturerAndStudentAreNotCreatedOutsideTheDemoProfile() {
        // These exist purely as convenience logins for local dev. Their presence in a
        // non-demo context means the gate has been bypassed.
        assertTrue(userRepository.findByUsername("lecturer").isEmpty(),
                "Demo lecturer account leaked outside the demo profile.");

        Optional<User> demoStudent = userRepository.findByUsername("S001");
        assertTrue(demoStudent.isEmpty(),
                "Demo student account leaked outside the demo profile.");
    }
}
