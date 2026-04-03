package com.chuka.irir.controller;

import com.chuka.irir.dto.AdminUserCreateDto;
import com.chuka.irir.exception.ResourceNotFoundException;
import com.chuka.irir.model.Role;
import com.chuka.irir.model.User;
import com.chuka.irir.repository.UserRepository;
import com.chuka.irir.service.UserService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.security.core.Authentication;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

    private final UserService userService;
    private final UserRepository userRepository;

    public AdminUserController(UserService userService, UserRepository userRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public String userManagement(Authentication authentication, Model model) {
        User admin = getCurrentUser(authentication);
        model.addAttribute("user", admin);
        model.addAttribute("pageTitle", "User Management");
        model.addAttribute("newUser", new AdminUserCreateDto());
        model.addAttribute("users", userService.findAll());
        model.addAttribute("availableRoles", Arrays.asList(Role.values()));
        return "admin/users";
    }

    @PostMapping
    public String createManagedUser(@Valid @ModelAttribute("newUser") AdminUserCreateDto newUser,
            BindingResult bindingResult,
            Authentication authentication,
            RedirectAttributes redirectAttributes,
            Model model) {
        User admin = getCurrentUser(authentication);

        if (bindingResult.hasErrors()) {
            populatePageModel(model, admin, newUser);
            return "admin/users";
        }

        try {
            User createdUser = userService.createManagedUser(newUser, admin);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Created account for " + createdUser.getFullName() + " (" + createdUser.getEmail() + ").");
            return "redirect:/admin/users";
        } catch (IllegalArgumentException ex) {
            bindingResult.reject("newUser", ex.getMessage());
            populatePageModel(model, admin, newUser);
            return "admin/users";
        }
    }

    @PostMapping("/{id}/roles")
    public String updateRoles(@PathVariable("id") Long userId,
            @RequestParam(name = "roles", required = false) Set<Role> roles,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        User admin = getCurrentUser(authentication);
        Set<Role> requestedRoles = roles == null ? Set.of() : new LinkedHashSet<>(roles);
        userService.updateUserRoles(userId, requestedRoles, admin);
        redirectAttributes.addFlashAttribute("successMessage", "User roles updated successfully.");
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/status")
    public String updateAccountStatus(@PathVariable("id") Long userId,
            @RequestParam("enabled") boolean enabled,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        User admin = getCurrentUser(authentication);
        userService.setAccountEnabled(userId, enabled, admin);
        redirectAttributes.addFlashAttribute("successMessage",
                enabled ? "User account enabled." : "User account disabled.");
        return "redirect:/admin/users";
    }

    private void populatePageModel(Model model, User admin, AdminUserCreateDto newUser) {
        model.addAttribute("user", admin);
        model.addAttribute("pageTitle", "User Management");
        model.addAttribute("newUser", newUser);
        model.addAttribute("users", userService.findAll());
        model.addAttribute("availableRoles", Arrays.asList(Role.values()));
    }

    private User getCurrentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ResourceNotFoundException("User", "authentication", "current session");
        }
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }
}
