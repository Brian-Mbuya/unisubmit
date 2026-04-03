package com.chuka.irir.controller;

import com.chuka.irir.dto.AdminLecturerUnitAssignmentFormDto;
import com.chuka.irir.dto.AdminProjectAssignmentDto;
import com.chuka.irir.dto.AdminCourseOptionDto;
import com.chuka.irir.dto.AdminUnitSummaryDto;
import com.chuka.irir.dto.CourseCreateDto;
import com.chuka.irir.dto.DepartmentCreateDto;
import com.chuka.irir.dto.ProjectAssignmentSummaryDto;
import com.chuka.irir.dto.UnitCreateDto;
import com.chuka.irir.model.Course;
import com.chuka.irir.exception.ResourceNotFoundException;
import com.chuka.irir.model.Department;
import com.chuka.irir.model.Role;
import com.chuka.irir.model.Unit;
import com.chuka.irir.model.User;
import com.chuka.irir.repository.UserRepository;
import com.chuka.irir.service.AcademicStructureService;
import com.chuka.irir.service.SubmissionWorkflowService;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.security.core.Authentication;

import java.util.Comparator;
import java.util.List;

@Controller
@RequestMapping("/admin/workflows")
public class AdminWorkflowController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final AcademicStructureService academicStructureService;
    private final SubmissionWorkflowService submissionWorkflowService;

    public AdminWorkflowController(UserService userService,
                                   UserRepository userRepository,
                                   AcademicStructureService academicStructureService,
                                   SubmissionWorkflowService submissionWorkflowService) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.academicStructureService = academicStructureService;
        this.submissionWorkflowService = submissionWorkflowService;
    }

    @GetMapping
    public String workflowManagement(Authentication authentication, Model model) {
        populatePageModel(model, getCurrentUser(authentication),
                new DepartmentCreateDto(),
                new CourseCreateDto(),
                new UnitCreateDto(),
                new AdminLecturerUnitAssignmentFormDto());
        return "admin/workflows";
    }

    @PostMapping("/departments")
    public String createDepartment(@Valid @ModelAttribute("departmentForm") DepartmentCreateDto departmentForm,
                                   BindingResult bindingResult,
                                   Authentication authentication,
                                   RedirectAttributes redirectAttributes,
                                   Model model) {
        User admin = getCurrentUser(authentication);
        if (bindingResult.hasErrors()) {
            populatePageModel(model, admin, departmentForm, new CourseCreateDto(), new UnitCreateDto(),
                    new AdminLecturerUnitAssignmentFormDto());
            return "admin/workflows";
        }

        try {
            Department department = academicStructureService.createDepartment(departmentForm);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Department created: " + department.getName() + ".");
            return "redirect:/admin/workflows";
        } catch (IllegalArgumentException ex) {
            bindingResult.reject("departmentForm", ex.getMessage());
            populatePageModel(model, admin, departmentForm, new CourseCreateDto(), new UnitCreateDto(),
                    new AdminLecturerUnitAssignmentFormDto());
            return "admin/workflows";
        }
    }

    @PostMapping("/courses")
    public String createCourse(@Valid @ModelAttribute("courseForm") CourseCreateDto courseForm,
                               BindingResult bindingResult,
                               Authentication authentication,
                               RedirectAttributes redirectAttributes,
                               Model model) {
        User admin = getCurrentUser(authentication);
        if (bindingResult.hasErrors()) {
            populatePageModel(model, admin, new DepartmentCreateDto(), courseForm, new UnitCreateDto(),
                    new AdminLecturerUnitAssignmentFormDto());
            return "admin/workflows";
        }

        try {
            Course course = academicStructureService.createCourse(courseForm);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Course created: " + course.getName() + ".");
            return "redirect:/admin/workflows";
        } catch (IllegalArgumentException ex) {
            bindingResult.reject("courseForm", ex.getMessage());
            populatePageModel(model, admin, new DepartmentCreateDto(), courseForm, new UnitCreateDto(),
                    new AdminLecturerUnitAssignmentFormDto());
            return "admin/workflows";
        }
    }

    @PostMapping("/units")
    public String createUnit(@Valid @ModelAttribute("unitForm") UnitCreateDto unitForm,
                             BindingResult bindingResult,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes,
                             Model model) {
        User admin = getCurrentUser(authentication);
        if (bindingResult.hasErrors()) {
            populatePageModel(model, admin, new DepartmentCreateDto(), new CourseCreateDto(), unitForm,
                    new AdminLecturerUnitAssignmentFormDto());
            return "admin/workflows";
        }

        try {
            Unit unit = academicStructureService.createUnit(unitForm);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Unit created: " + unit.getCode() + " " + unit.getName() + ".");
            return "redirect:/admin/workflows";
        } catch (IllegalArgumentException ex) {
            bindingResult.reject("unitForm", ex.getMessage());
            populatePageModel(model, admin, new DepartmentCreateDto(), new CourseCreateDto(), unitForm,
                    new AdminLecturerUnitAssignmentFormDto());
            return "admin/workflows";
        }
    }

    @PostMapping("/lecturer-units")
    public String assignUnitsToLecturer(
            @Valid @ModelAttribute("lecturerUnitAssignmentForm") AdminLecturerUnitAssignmentFormDto lecturerUnitAssignmentForm,
            BindingResult bindingResult,
            Authentication authentication,
            RedirectAttributes redirectAttributes,
            Model model) {
        User admin = getCurrentUser(authentication);
        if (bindingResult.hasErrors()) {
            populatePageModel(model, admin, new DepartmentCreateDto(), new CourseCreateDto(), new UnitCreateDto(),
                    lecturerUnitAssignmentForm);
            return "admin/workflows";
        }

        try {
            academicStructureService.assignUnitsToLecturer(
                    lecturerUnitAssignmentForm.getLecturerId(),
                    lecturerUnitAssignmentForm.getUnitIds());
            User lecturer = userService.findById(lecturerUnitAssignmentForm.getLecturerId());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Updated unit teaching assignments for " + lecturer.getFullName() + ".");
            return "redirect:/admin/workflows";
        } catch (IllegalArgumentException ex) {
            bindingResult.reject("lecturerUnitAssignmentForm", ex.getMessage());
            populatePageModel(model, admin, new DepartmentCreateDto(), new CourseCreateDto(), new UnitCreateDto(),
                    lecturerUnitAssignmentForm);
            return "admin/workflows";
        }
    }

    @PostMapping("/projects/{projectId}/lecturer")
    public String assignLecturerToProject(@PathVariable Long projectId,
                                          @Valid @ModelAttribute("projectAssignment") AdminProjectAssignmentDto projectAssignment,
                                          BindingResult bindingResult,
                                          Authentication authentication,
                                          RedirectAttributes redirectAttributes,
                                          Model model) {
        User admin = getCurrentUser(authentication);
        if (bindingResult.hasErrors()) {
            populatePageModel(model, admin, new DepartmentCreateDto(), new CourseCreateDto(), new UnitCreateDto(),
                    new AdminLecturerUnitAssignmentFormDto());
            model.addAttribute("assignmentErrorProjectId", projectId);
            return "admin/workflows";
        }

        try {
            submissionWorkflowService.assignLecturerToFinalYearProject(projectId, projectAssignment);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Lecturer assignment updated for project #" + projectId + ".");
            return "redirect:/admin/workflows";
        } catch (IllegalArgumentException ex) {
            populatePageModel(model, admin, new DepartmentCreateDto(), new CourseCreateDto(), new UnitCreateDto(),
                    new AdminLecturerUnitAssignmentFormDto());
            model.addAttribute("assignmentErrorProjectId", projectId);
            model.addAttribute("assignmentErrorMessage", ex.getMessage());
            return "admin/workflows";
        }
    }

    private void populatePageModel(Model model,
                                   User admin,
                                   DepartmentCreateDto departmentForm,
                                   CourseCreateDto courseForm,
                                   UnitCreateDto unitForm,
                                   AdminLecturerUnitAssignmentFormDto lecturerUnitAssignmentForm) {
        List<Department> departments = academicStructureService.listDepartments().stream()
                .sorted(Comparator.comparing(Department::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<AdminCourseOptionDto> courses = academicStructureService.listCourseOptions();
        List<AdminUnitSummaryDto> units = academicStructureService.listUnitSummaries();
        List<User> lecturers = userService.findByRole(Role.SUPERVISOR).stream()
                .sorted(Comparator.comparing(User::getFullName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<ProjectAssignmentSummaryDto> projectAssignments = submissionWorkflowService.getFinalYearAssignmentsOverview().stream()
                .sorted(Comparator.comparing(ProjectAssignmentSummaryDto::projectId).reversed())
                .toList();

        model.addAttribute("user", admin);
        model.addAttribute("pageTitle", "Workflow Management");
        model.addAttribute("departmentForm", departmentForm);
        model.addAttribute("courseForm", courseForm);
        model.addAttribute("unitForm", unitForm);
        model.addAttribute("lecturerUnitAssignmentForm", lecturerUnitAssignmentForm);
        model.addAttribute("departments", departments);
        model.addAttribute("courses", courses);
        model.addAttribute("units", units);
        model.addAttribute("lecturers", lecturers);
        model.addAttribute("projectAssignments", projectAssignments);
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
