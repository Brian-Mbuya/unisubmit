package com.chuka.irir.controller;

import com.chuka.irir.dto.SubmissionReviewRequestDto;
import com.chuka.irir.dto.SubmissionDetailDto;
import com.chuka.irir.dto.SubmissionSummaryDto;
import com.chuka.irir.model.LecturerUnitAssignment;
import com.chuka.irir.model.Review;
import com.chuka.irir.model.Unit;
import com.chuka.irir.model.User;
import com.chuka.irir.repository.UserRepository;
import com.chuka.irir.service.AcademicStructureService;
import com.chuka.irir.service.SubmissionWorkflowService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/lecturer")
public class LecturerSubmissionApiController {

    private final AcademicStructureService academicStructureService;
    private final SubmissionWorkflowService submissionWorkflowService;
    private final UserRepository userRepository;

    public LecturerSubmissionApiController(AcademicStructureService academicStructureService,
                                           SubmissionWorkflowService submissionWorkflowService,
                                           UserRepository userRepository) {
        this.academicStructureService = academicStructureService;
        this.submissionWorkflowService = submissionWorkflowService;
        this.userRepository = userRepository;
    }

    @GetMapping("/unit-assignments")
    public ResponseEntity<List<LecturerUnitAssignment>> myUnitAssignments(Authentication authentication) {
        User lecturer = getCurrentLecturer(authentication);
        return ResponseEntity.ok(academicStructureService.listAssignmentsForLecturer(lecturer.getId()));
    }

    @GetMapping("/units")
    public ResponseEntity<List<Unit>> myUnits(Authentication authentication) {
        User lecturer = getCurrentLecturer(authentication);
        return ResponseEntity.ok(academicStructureService.listAssignedUnitsForLecturer(lecturer.getId()));
    }

    @GetMapping("/submissions")
    public ResponseEntity<List<SubmissionSummaryDto>> assignedSubmissions(Authentication authentication,
                                                                         @RequestParam(required = false) Long unitId) {
        User lecturer = getCurrentLecturer(authentication);
        return ResponseEntity.ok(submissionWorkflowService.getLecturerSubmissionSummaries(lecturer.getId(), unitId));
    }

    @GetMapping("/submissions/{submissionId}")
    public ResponseEntity<SubmissionDetailDto> viewSubmission(@PathVariable Long submissionId,
                                                              Authentication authentication) {
        User lecturer = getCurrentLecturer(authentication);
        return ResponseEntity.ok(submissionWorkflowService.getSubmissionDetailForLecturer(submissionId, lecturer.getId()));
    }

    @PostMapping("/submissions/{submissionId}/reviews")
    public ResponseEntity<Review> reviewSubmission(@PathVariable Long submissionId,
                                                   @Valid @RequestBody SubmissionReviewRequestDto dto,
                                                   Authentication authentication) {
        User lecturer = getCurrentLecturer(authentication);
        return ResponseEntity.ok(submissionWorkflowService.reviewSubmission(submissionId, lecturer.getId(), dto));
    }

    @GetMapping("/submissions/{submissionId}/files/{fileId}")
    public ResponseEntity<Resource> downloadSubmissionFile(@PathVariable Long submissionId,
                                                           @PathVariable Long fileId,
                                                           Authentication authentication) {
        User lecturer = getCurrentLecturer(authentication);
        Resource resource = submissionWorkflowService.downloadSubmissionFileForLecturer(
                submissionId,
                fileId,
                lecturer.getId()
        );
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment")
                .body(resource);
    }

    @PostMapping("/submissions/{submissionId}/analysis/re-run")
    public ResponseEntity<Void> rerunAnalysis(@PathVariable Long submissionId, Authentication authentication) {
        User lecturer = getCurrentLecturer(authentication);
        submissionWorkflowService.requestReanalysis(submissionId, lecturer.getId(), true);
        return ResponseEntity.accepted().build();
    }

    private User getCurrentLecturer(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("Authenticated lecturer not found."));
    }
}
