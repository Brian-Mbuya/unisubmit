package com.unisubmit.controller.admin;

import com.unisubmit.domain.LecturerProfile;
import com.unisubmit.domain.Role;
import com.unisubmit.domain.StudentProfile;
import com.unisubmit.domain.User;
import com.unisubmit.repository.LecturerProfileRepository;
import com.unisubmit.repository.StudentProfileRepository;
import com.unisubmit.security.CustomUserDetails;
import com.unisubmit.service.AcademicHierarchyService;
import com.unisubmit.service.CourseService;
import com.unisubmit.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/accounts")
public class AdminAccountController {

    private final UserService userService;
    private final AcademicHierarchyService academicHierarchyService;
    private final CourseService courseService;
    private final StudentProfileRepository studentProfileRepository;
    private final LecturerProfileRepository lecturerProfileRepository;

    public AdminAccountController(UserService userService,
                                  AcademicHierarchyService academicHierarchyService,
                                  CourseService courseService,
                                  StudentProfileRepository studentProfileRepository,
                                  LecturerProfileRepository lecturerProfileRepository) {
        this.userService = userService;
        this.academicHierarchyService = academicHierarchyService;
        this.courseService = courseService;
        this.studentProfileRepository = studentProfileRepository;
        this.lecturerProfileRepository = lecturerProfileRepository;
    }

    @GetMapping
    public String listAccounts(@RequestParam(required = false) Role role, Model model) {
        List<User> users;
        if (role != null) {
            users = userService.findByRole(role);
        } else {
            users = userService.findAll();
        }
        model.addAttribute("users", users);
        model.addAttribute("departments", academicHierarchyService.findAllDepartments());
        model.addAttribute("courses", courseService.findAll());
        model.addAttribute("selectedRole", role != null ? role.name() : "ALL");
        return "admin/accounts";
    }

    @PostMapping
    public String createAccount(@RequestParam String name,
                                @RequestParam String email,
                                @RequestParam String password,
                                @RequestParam Role role,
                                @RequestParam(required = false) String studentId,
                                @RequestParam(required = false) String staffId,
                                @RequestParam(required = false) Long departmentId,
                                @RequestParam(required = false) Long courseId,
                                @RequestParam(required = false) Integer currentYear,
                                @RequestParam(required = false) Integer currentSemester,
                                RedirectAttributes ra) {
        try {
            userService.createUser(email, password, name, role, studentId, staffId, departmentId, courseId, currentYear, currentSemester);
            ra.addFlashAttribute("success", "Account created successfully.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error creating account: " + e.getMessage());
        }
        return "redirect:/admin/accounts";
    }

    @PostMapping("/student/{id}/update")
    public String updateStudent(@PathVariable Long id,
                                @RequestParam(required = false) Long programmeId,
                                @RequestParam(required = false) Integer currentYear,
                                @RequestParam(required = false) Integer currentSemester,
                                @RequestParam(required = false) String academicStatus,
                                RedirectAttributes ra) {
        try {
            StudentProfile profile = studentProfileRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Student profile not found: " + id));

            if (programmeId != null) {
                courseService.findAll().stream()
                        .filter(c -> c.getId().equals(programmeId))
                        .findFirst()
                        .ifPresent(profile::setProgramme);
            } else {
                profile.setProgramme(null);
            }
            profile.setCurrentYear(currentYear);
            profile.setCurrentSemester(currentSemester);
            if (academicStatus != null && !academicStatus.isBlank()) {
                profile.setAcademicStatus(academicStatus.trim());
            }
            studentProfileRepository.save(profile);
            ra.addFlashAttribute("success", "Student profile updated successfully.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Could not update student: " + e.getMessage());
        }
        return "redirect:/admin/accounts";
    }

    @PostMapping("/lecturer/{id}/update")
    public String updateLecturer(@PathVariable Long id,
                                 @RequestParam(required = false) Long departmentId,
                                 @RequestParam(required = false) String academicRank,
                                 @RequestParam(required = false) String employmentType,
                                 @RequestParam(required = false) String specialization,
                                 @RequestParam(required = false) String office,
                                 @RequestParam(required = false) String phone,
                                 RedirectAttributes ra) {
        try {
            LecturerProfile profile = lecturerProfileRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Lecturer profile not found: " + id));

            if (departmentId != null) {
                profile.setDepartment(academicHierarchyService.findDepartmentById(departmentId));
            } else {
                profile.setDepartment(null);
            }
            profile.setAcademicRank(academicRank);
            profile.setEmploymentType(employmentType);
            profile.setSpecialization(specialization);
            profile.setOffice(office);
            profile.setPhone(phone);
            lecturerProfileRepository.save(profile);
            ra.addFlashAttribute("success", "Lecturer profile updated successfully.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Could not update lecturer: " + e.getMessage());
        }
        return "redirect:/admin/accounts";
    }

    @PostMapping("/{id}/delete")
    public String deleteAccount(@PathVariable Long id,
                                @AuthenticationPrincipal CustomUserDetails currentUser,
                                RedirectAttributes ra) {
        try {
            userService.deleteUser(id, currentUser.getUsername());
            ra.addFlashAttribute("success", "Account deleted successfully.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Could not delete account: " + e.getMessage());
        }
        return "redirect:/admin/accounts";
    }
}
