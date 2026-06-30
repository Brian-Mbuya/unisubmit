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

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String registerForm() {
        return "register";
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
