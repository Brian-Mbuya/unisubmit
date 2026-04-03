package com.chuka.irir.controller;

import com.chuka.irir.dto.CourseworkSubmissionCreateDto;
import com.chuka.irir.dto.FinalYearSubmissionCreateDto;
import com.chuka.irir.dto.SubmissionDetailDto;
import com.chuka.irir.dto.SubmissionSummaryDto;
import com.chuka.irir.dto.UnitOptionDto;
import com.chuka.irir.model.User;
import com.chuka.irir.repository.UserRepository;
import com.chuka.irir.service.AcademicStructureService;
import com.chuka.irir.service.SubmissionWorkflowService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/student")
public class StudentSubmissionApiController {

    private final SubmissionWorkflowService submissionWorkflowService;
    private final AcademicStructureService academicStructureService;
    private final UserRepository userRepository;

    public StudentSubmissionApiController(SubmissionWorkflowService submissionWorkflowService,
                                          AcademicStructureService academicStructureService,
                                          UserRepository userRepository) {
        this.submissionWorkflowService = submissionWorkflowService;
        this.academicStructureService = academicStructureService;
        this.userRepository = userRepository;
    }

    @PostMapping(path = "/final-year-submissions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SubmissionSummaryDto> submitFinalYear(@Valid @ModelAttribute FinalYearSubmissionCreateDto dto,
                                                                Authentication authentication) {
        User student = getCurrentStudent(authentication);
        submissionWorkflowService.submitFinalYearProject(dto, student);
        SubmissionSummaryDto latest = submissionWorkflowService.getStudentSubmissionSummaries(student.getId()).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Submission was created but could not be loaded."));
        return ResponseEntity.ok(latest);
    }

    @PostMapping(path = "/coursework-submissions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SubmissionSummaryDto> submitCoursework(@Valid @ModelAttribute CourseworkSubmissionCreateDto dto,
                                                                 Authentication authentication) {
        User student = getCurrentStudent(authentication);
        submissionWorkflowService.submitCoursework(dto, student);
        SubmissionSummaryDto latest = submissionWorkflowService.getStudentSubmissionSummaries(student.getId()).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Submission was created but could not be loaded."));
        return ResponseEntity.ok(latest);
    }

    @GetMapping("/courses/{courseId}/units")
    public ResponseEntity<List<UnitOptionDto>> unitsByCourse(@PathVariable Long courseId) {
        return ResponseEntity.ok(academicStructureService.listUnitsWithLecturer(courseId));
    }

    @GetMapping("/submissions")
    public ResponseEntity<List<SubmissionSummaryDto>> mySubmissions(Authentication authentication) {
        return ResponseEntity.ok(submissionWorkflowService
                .getStudentSubmissionSummaries(getCurrentStudent(authentication).getId()));
    }

    @GetMapping("/submissions/{submissionId}")
    public ResponseEntity<SubmissionDetailDto> viewSubmission(@PathVariable Long submissionId,
                                                              Authentication authentication) {
        SubmissionDetailDto submission = submissionWorkflowService.getSubmissionDetailForStudent(
                submissionId,
                getCurrentStudent(authentication).getId()
        );
        return ResponseEntity.ok(submission);
    }

    @PostMapping("/submissions/{submissionId}/analysis/re-run")
    public ResponseEntity<Void> rerunAnalysis(@PathVariable Long submissionId, Authentication authentication) {
        submissionWorkflowService.requestReanalysis(
                submissionId,
                getCurrentStudent(authentication).getId(),
                false
        );
        return ResponseEntity.accepted().build();
    }

    private User getCurrentStudent(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("Authenticated student not found."));
    }
}
