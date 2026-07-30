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
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return "redirect:/";
        }
        return "login";
    }

    @GetMapping("/register")
    public String registerForm() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return "redirect:/";
        }
        return "register";
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordForm() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return "redirect:/";
        }
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
                           @RequestParam String phone,
                           @RequestParam String password,
                           @RequestParam String studentId,
                           @RequestParam(required = false) Long courseId,
                           @RequestParam(required = false) Long departmentId,
                           @RequestParam(required = false) Long facultyId,
                           Model model) {
        // Students are identified by admission number + phone. No student email exists.
        String validationError = null;
        if (name == null || name.trim().length() < 2) {
            validationError = "Full name must be at least 2 characters.";
        } else if (studentId == null || studentId.trim().isEmpty()) {
            validationError = "Admission number is required.";
        } else if (!com.unisubmit.service.UserService.isValidPhone(phone)) {
            validationError = "Enter a valid phone number (9–15 digits, e.g. 0712345678).";
        } else if (password == null || password.length() < 6) {
            validationError = "Password must be at least 6 characters.";
        }

        if (validationError != null) {
            return backToForm(model, validationError, name, phone, studentId, courseId, departmentId, facultyId);
        }

        try {
            userService.createStudent(studentId, phone, password, name, courseId, 1, 1);
            return "redirect:/login?registered";
        } catch (RuntimeException ex) {
            return backToForm(model, ex.getMessage(), name, phone, studentId, courseId, departmentId, facultyId);
        }
    }

    /** Re-renders the registration form with the entered values preserved. */
    private String backToForm(Model model, String error, String name, String phone, String studentId,
                              Long courseId, Long departmentId, Long facultyId) {
        model.addAttribute("error", error);
        model.addAttribute("formName", name);
        model.addAttribute("formPhone", phone);
        model.addAttribute("formStudentId", studentId);
        model.addAttribute("formCourseId", courseId);
        model.addAttribute("formDepartmentId", departmentId);
        model.addAttribute("formFacultyId", facultyId);
        return "register";
    }

    /** Public pitch page — linked from the login screen, safe for logged-out visitors. */
    @GetMapping("/about")
    public String about() {
        return "about";
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
