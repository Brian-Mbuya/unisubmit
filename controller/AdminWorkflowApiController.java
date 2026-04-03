package com.chuka.irir.controller;

import com.chuka.irir.dto.AdminProjectAssignmentDto;
import com.chuka.irir.dto.AdminUserCreateDto;
import com.chuka.irir.dto.CourseCreateDto;
import com.chuka.irir.dto.DepartmentCreateDto;
import com.chuka.irir.dto.LecturerUnitAssignmentRequestDto;
import com.chuka.irir.dto.ProjectAssignmentSummaryDto;
import com.chuka.irir.dto.UnitCreateDto;
import com.chuka.irir.model.LecturerUnitAssignment;
import com.chuka.irir.model.Course;
import com.chuka.irir.model.Department;
import com.chuka.irir.model.Project;
import com.chuka.irir.model.Role;
import com.chuka.irir.model.Unit;
import com.chuka.irir.model.User;
import com.chuka.irir.repository.UserRepository;
import com.chuka.irir.service.AcademicStructureService;
import com.chuka.irir.service.SubmissionWorkflowService;
import com.chuka.irir.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminWorkflowApiController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final AcademicStructureService academicStructureService;
    private final SubmissionWorkflowService submissionWorkflowService;

    public AdminWorkflowApiController(UserService userService,
                                      UserRepository userRepository,
                                      AcademicStructureService academicStructureService,
                                      SubmissionWorkflowService submissionWorkflowService) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.academicStructureService = academicStructureService;
        this.submissionWorkflowService = submissionWorkflowService;
    }

    @PostMapping("/lecturers")
    public ResponseEntity<User> createLecturer(@Valid @RequestBody AdminUserCreateDto dto, Authentication authentication) {
        User admin = getCurrentUser(authentication);
        if (dto.getRoles() == null || dto.getRoles().isEmpty()) {
            dto.setRoles(new HashSet<>(List.of(Role.SUPERVISOR)));
        } else {
            dto.getRoles().add(Role.SUPERVISOR);
        }
        User lecturer = userService.createManagedUser(dto, admin);
        if (dto.getUnitIds() != null && !dto.getUnitIds().isEmpty()) {
            academicStructureService.assignUnitsToLecturer(lecturer.getId(), dto.getUnitIds());
        }
        return ResponseEntity.ok(lecturer);
    }

    @PostMapping("/departments")
    public ResponseEntity<Department> createDepartment(@Valid @RequestBody DepartmentCreateDto dto) {
        return ResponseEntity.ok(academicStructureService.createDepartment(dto));
    }

    @PostMapping("/courses")
    public ResponseEntity<Course> createCourse(@Valid @RequestBody CourseCreateDto dto) {
        return ResponseEntity.ok(academicStructureService.createCourse(dto));
    }

    @PostMapping("/units")
    public ResponseEntity<Unit> createUnit(@Valid @RequestBody UnitCreateDto dto) {
        return ResponseEntity.ok(academicStructureService.createUnit(dto));
    }

    @PostMapping("/final-year-projects/{projectId}/assign")
    public ResponseEntity<Project> assignLecturer(@PathVariable Long projectId,
                                                  @Valid @RequestBody AdminProjectAssignmentDto dto) {
        return ResponseEntity.ok(submissionWorkflowService.assignLecturerToFinalYearProject(projectId, dto));
    }

    @PostMapping("/lecturers/{lecturerId}/unit-assignments")
    public ResponseEntity<List<LecturerUnitAssignment>> assignUnitsToLecturer(@PathVariable Long lecturerId,
                                                                              @Valid @RequestBody LecturerUnitAssignmentRequestDto dto) {
        return ResponseEntity.ok(academicStructureService.assignUnitsToLecturer(lecturerId, dto.getUnitIds()));
    }

    @GetMapping("/final-year-projects/assignments")
    public ResponseEntity<List<ProjectAssignmentSummaryDto>> viewAssignments() {
        return ResponseEntity.ok(submissionWorkflowService.getFinalYearAssignmentsOverview());
    }

    private User getCurrentUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("Authenticated admin not found."));
    }
}
