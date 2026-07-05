package com.unisubmit.controller;

import com.unisubmit.domain.Role;
import com.unisubmit.service.UserService;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

    private final UserService userService;
    private final com.unisubmit.service.NotificationService notificationService;

    public AuthController(UserService userService,
                          com.unisubmit.service.NotificationService notificationService) {
        this.userService = userService;
        this.notificationService = notificationService;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String registerForm() {
        return "register";
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordForm() {
        return "forgot-password";
    }

    /**
     * Request-based reset (no email infrastructure): notifies every admin so
     * they can reset the account from the admin console. Always shows the same
     * confirmation regardless of whether the identifier exists (no enumeration).
     */
    @PostMapping("/forgot-password")
    public String requestPasswordReset(@RequestParam String identifier,
                                       @RequestParam(required = false) String note) {
        String id = identifier == null ? "" : identifier.trim();
        if (!id.isEmpty()) {
            String message = "Password reset requested for '" + id + "'"
                    + (note != null && !note.isBlank() ? " — note: " + note.trim() : "")
                    + ". Reset it from Admin › Accounts.";
            for (var admin : userService.findByRole(Role.ADMIN)) {
                notificationService.createNotification(
                        admin, com.unisubmit.domain.NotificationType.SYSTEM_NOTICE, message, null);
            }
        }
        return "redirect:/login?resetRequested";
    }

    @PostMapping("/register")
    public String register(@RequestParam String name,
                           @RequestParam String email,
                           @RequestParam String password,
                           @RequestParam String studentId,
                           @RequestParam(required = false) Long courseId,
                           @RequestParam(required = false) Long departmentId,
                           Model model) {
        // Basic input validation
        String validationError = null;
        if (name == null || name.trim().length() < 2) {
            validationError = "Full name must be at least 2 characters.";
        } else if (email == null || !email.trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            validationError = "Please enter a valid email address.";
        } else if (password == null || password.length() < 6) {
            validationError = "Password must be at least 6 characters.";
        } else if (studentId == null || studentId.trim().isEmpty()) {
            validationError = "Student ID is required.";
        }

        if (validationError != null) {
            model.addAttribute("error", validationError);
            model.addAttribute("formName", name);
            model.addAttribute("formEmail", email);
            model.addAttribute("formStudentId", studentId);
            model.addAttribute("formCourseId", courseId);
            model.addAttribute("formDepartmentId", departmentId);
            return "register";
        }

        try {
            userService.createUser(email.trim(), password, name.trim(), Role.STUDENT, studentId, null, departmentId, courseId, 1, 1);
            return "redirect:/login?registered";
        } catch (RuntimeException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("formName", name);
            model.addAttribute("formEmail", email);
            model.addAttribute("formStudentId", studentId);
            model.addAttribute("formCourseId", courseId);
            model.addAttribute("formDepartmentId", departmentId);
            return "register";
        }
    }

    @GetMapping("/")
    public String index() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
                return "redirect:/admin/dashboard";
            } else if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_LECTURER"))) {
                return "redirect:/lecturer/dashboard";
            } else if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_STUDENT"))) {
                return "redirect:/student/dashboard";
            }
        }
        return "redirect:/login";
    }
}
