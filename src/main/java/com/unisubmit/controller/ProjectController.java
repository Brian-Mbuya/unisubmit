package com.unisubmit.controller;

import com.unisubmit.domain.Submission;
import com.unisubmit.domain.User;
import com.unisubmit.repository.SubmissionRepository;
import com.unisubmit.security.CustomUserDetails;
import com.unisubmit.service.SubmissionAccessService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.transaction.annotation.Transactional;

@Controller
public class ProjectController {

    private final SubmissionRepository submissionRepository;
    private final SubmissionAccessService submissionAccessService;

    public ProjectController(SubmissionRepository submissionRepository,
                             SubmissionAccessService submissionAccessService) {
        this.submissionRepository = submissionRepository;
        this.submissionAccessService = submissionAccessService;
    }

    @Transactional(readOnly = true)
    @GetMapping("/projects/{id}")
    public String viewProjectDetail(@AuthenticationPrincipal CustomUserDetails userDetails,
                                    @PathVariable Long id, Model model) {
        Submission submission = submissionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        User currentUser = userDetails != null ? userDetails.getUser() : null;
        model.addAttribute("submission", submission);
        model.addAttribute("canAccessFile", submissionAccessService.canAccessSubmissionFile(currentUser, submission));
        return "student/project-detail";
    }
}
