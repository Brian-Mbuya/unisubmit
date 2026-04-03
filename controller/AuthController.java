package com.chuka.irir.controller;

import com.chuka.irir.dto.UserRegistrationDto;
import com.chuka.irir.service.PasswordResetService;
import com.chuka.irir.service.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Objects;

/**
 * Controller for authentication pages: login, registration, logout, and password reset.
 */
@Controller
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;
    private final PasswordResetService passwordResetService;

    public AuthController(UserService userService, PasswordResetService passwordResetService) {
        this.userService = userService;
        this.passwordResetService = passwordResetService;
    }

    // ==================== Login ====================

    @GetMapping("/login")
    public String showLoginPage() {
        return "login"; // handled by Spring Security POST /login
    }

    // ==================== Registration ====================

    @GetMapping("/register")
    public String showRegistrationForm(Model model) {
        model.addAttribute("user", new UserRegistrationDto());
        return "register";
    }

    @PostMapping("/register")
    public String registerStudent(@Valid @ModelAttribute("user") UserRegistrationDto userDto,
                                  BindingResult bindingResult,
                                  RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            return "register";
        }

        if (userDto.getPassword() == null || !userDto.getPassword().equals(userDto.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "error.user", "Passwords do not match");
            return "register";
        }

        try {
            userService.registerStudent(userDto);
            logger.info("New student registered successfully: {}", userDto.getEmail());

            redirectAttributes.addFlashAttribute("successMessage",
                    "Registration successful! Please log in with your credentials.");
            return "redirect:/login";

        } catch (IllegalArgumentException e) {
            String errorMsg = (e.getMessage() != null && !e.getMessage().isBlank())
                    ? e.getMessage()
                    : "Email already in use.";

            logger.warn("Registration failed: {}", errorMsg, e);

            bindingResult.rejectValue("email", "error.user", errorMsg);

            return "register";
        }
    }

    // ==================== Forgot Password ====================

    @GetMapping("/forgot-password")
    public String showForgotPasswordForm() {
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String processForgotPassword(@RequestParam("email") String email,
                                        RedirectAttributes redirectAttributes) {
        String normalizedEmail = email == null ? "" : email.trim();
        if (normalizedEmail.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Email is required.");
            return "redirect:/forgot-password";
        }

        try {
            passwordResetService.initiatePasswordReset(normalizedEmail);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Reset link sent to " + normalizedEmail + ". Check your inbox.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    Objects.toString(e.getMessage(), "An unexpected error occurred."));
        }
        return "redirect:/forgot-password";
    }

    // ==================== Reset Password ====================

    @GetMapping("/reset-password")
    public String showResetPasswordForm(@RequestParam("token") String token, Model model,
                                        RedirectAttributes redirectAttributes) {
        if (!passwordResetService.isTokenValid(token)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Invalid or expired token.");
            return "redirect:/login";
        }
        model.addAttribute("token", token);
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String processResetPassword(@RequestParam("token") String token,
                                       @RequestParam("newPassword") String newPassword,
                                       @RequestParam("confirmPassword") String confirmPassword,
                                       RedirectAttributes redirectAttributes) {

        if (newPassword == null || newPassword.isBlank() || confirmPassword == null || confirmPassword.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Both password fields are required.");
            return "redirect:/reset-password?token=" + token;
        }

        if (!Objects.equals(newPassword, confirmPassword)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Passwords do not match.");
            return "redirect:/reset-password?token=" + token;
        }

        try {
            passwordResetService.resetPassword(token, newPassword);
            redirectAttributes.addFlashAttribute("successMessage", "Password reset successful! Please log in.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    Objects.toString(e.getMessage(), "Password reset failed."));
            return "redirect:/reset-password?token=" + token;
        }

        return "redirect:/login";
    }
}
