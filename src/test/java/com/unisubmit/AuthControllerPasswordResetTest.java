package com.unisubmit;

import com.unisubmit.domain.Role;
import com.unisubmit.domain.User;
import com.unisubmit.repository.UserRepository;
import com.unisubmit.service.EmailService;
import com.unisubmit.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end (through MockMvc) coverage of the two forgot-password branches: lecturers/
 * admins get a real emailed code (verified here via a mocked {@link EmailService}, so no
 * SMTP call is ever attempted), students fall back to the pre-existing admin-mediated
 * path and never trigger an email.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerPasswordResetTest {

    @Autowired private MockMvc mvc;
    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockitoBean private EmailService emailService;

    private User lecturer;

    @BeforeEach
    void setUp() {
        lecturer = userService.createUser("dr.reset@university.edu", "originalPass1", "Dr. Reset",
                Role.LECTURER, null, "L-RESET-1");
        userService.createStudent("SCT-RESET-1", "0712340099", "originalPass1", "Reset Student", null, 1, 1);
    }

    @Test
    void lecturerRequestSendsAnEmailedCodeAndRedirectsToResetPage() throws Exception {
        mvc.perform(post("/forgot-password").with(csrf())
                        .param("identifier", "dr.reset@university.edu"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/reset-password?sent"));

        verify(emailService).sendPasswordResetCode(any(User.class), anyString(), any(Duration.class));
    }

    @Test
    void studentRequestNeverTriggersAnEmailAndUsesTheAdminMediatedPath() throws Exception {
        mvc.perform(post("/forgot-password").with(csrf())
                        .param("identifier", "SCT-RESET-1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?resetRequested"));

        verify(emailService, never()).sendPasswordResetCode(any(), anyString(), any());
    }

    @Test
    void unknownIdentifierAlsoUsesTheAdminMediatedPath() throws Exception {
        mvc.perform(post("/forgot-password").with(csrf())
                        .param("identifier", "nobody@nowhere.example"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?resetRequested"));

        verify(emailService, never()).sendPasswordResetCode(any(), anyString(), any());
    }

    @Test
    void correctCodeResetsThePasswordAndSignsInWithTheNewOne() throws Exception {
        mvc.perform(post("/forgot-password").with(csrf())
                .param("identifier", "dr.reset@university.edu"));

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetCode(any(User.class), codeCaptor.capture(), any(Duration.class));
        String code = codeCaptor.getValue();

        mvc.perform(post("/reset-password").with(csrf())
                        .param("identifier", "dr.reset@university.edu")
                        .param("code", code)
                        .param("newPassword", "brandNewPass1")
                        .param("confirmPassword", "brandNewPass1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?reset"));

        User reloaded = userRepository.findByUsername("dr.reset@university.edu").orElseThrow();
        assertTrue(passwordEncoder.matches("brandNewPass1", reloaded.getPassword()));
    }

    @Test
    void wrongCodeIsRejectedWithAGenericError() throws Exception {
        mvc.perform(post("/forgot-password").with(csrf())
                .param("identifier", "dr.reset@university.edu"));

        mvc.perform(post("/reset-password").with(csrf())
                        .param("identifier", "dr.reset@university.edu")
                        .param("code", "000000")
                        .param("newPassword", "brandNewPass1")
                        .param("confirmPassword", "brandNewPass1"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("error", "Invalid or expired code."));

        User reloaded = userRepository.findByUsername("dr.reset@university.edu").orElseThrow();
        assertTrue(passwordEncoder.matches("originalPass1", reloaded.getPassword()));
    }

    @Test
    void aStudentIdentifierIsRejectedByResetPasswordEvenWithAnArbitraryCode() throws Exception {
        mvc.perform(post("/reset-password").with(csrf())
                        .param("identifier", "SCT-RESET-1")
                        .param("code", "123456")
                        .param("newPassword", "brandNewPass1")
                        .param("confirmPassword", "brandNewPass1"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("error", "Invalid or expired code."));
    }

    @Test
    void mismatchedConfirmationIsRejectedBeforeTouchingTheCode() throws Exception {
        mvc.perform(post("/reset-password").with(csrf())
                        .param("identifier", "dr.reset@university.edu")
                        .param("code", "123456")
                        .param("newPassword", "brandNewPass1")
                        .param("confirmPassword", "somethingElse1"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("error", "Passwords do not match."));

        verify(emailService, never()).sendPasswordResetCode(any(), anyString(), any());
    }

    @Test
    void resetPasswordPageIsPubliclyReachable() throws Exception {
        mvc.perform(get("/reset-password")).andExpect(status().isOk());
    }
}
