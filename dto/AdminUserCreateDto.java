package com.chuka.irir.dto;

import com.chuka.irir.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * DTO for admin-created user accounts such as lecturers and directorate staff.
 */
@Getter
@Setter
public class AdminUserCreateDto {

    @NotBlank(message = "First name is required")
    @Size(min = 2, max = 50, message = "First name must be between 2 and 50 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(min = 2, max = 50, message = "Last name must be between 2 and 50 characters")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    private String email;

    @Size(max = 50, message = "Staff/registration number must be 50 characters or less")
    private String regNumber;

    @Size(max = 100, message = "Department must be 100 characters or less")
    private String department;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
    private String password;

    @NotBlank(message = "Please confirm the password")
    private String confirmPassword;

    @NotEmpty(message = "Select at least one role")
    private Set<Role> roles = new LinkedHashSet<>();

    private Set<Long> unitIds = new LinkedHashSet<>();
}
