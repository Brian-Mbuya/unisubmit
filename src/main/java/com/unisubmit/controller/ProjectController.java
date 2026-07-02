package com.unisubmit.controller;

import com.unisubmit.domain.RelationType;
import com.unisubmit.domain.Role;
import com.unisubmit.domain.Submission;
import com.unisubmit.domain.User;
import com.unisubmit.repository.SubmissionRepository;
import com.unisubmit.security.CustomUserDetails;
import com.unisubmit.service.AuditService;
import com.unisubmit.service.SubmissionAccessService;
import com.unisubmit.service.SubmissionRelationService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.stream.Collectors;

@Controller
public class ProjectController {

    private final SubmissionRepository submissionRepository;
    private final SubmissionAccessService submissionAccessService;
    private final AuditService auditService;
    private final SubmissionRelationService relationService;

    public ProjectController(SubmissionRepository submissionRepository,
                             SubmissionAccessService submissionAccessService,
                             AuditService auditService,
                             SubmissionRelationService relationService) {
        this.submissionRepository = submissionRepository;
        this.submissionAccessService = submissionAccessService;
        this.auditService = auditService;
        this.relationService = relationService;
    }

    @Transactional(readOnly = true)
    @GetMapping("/projects/{id}")
    public String viewProjectDetail(@AuthenticationPrincipal CustomUserDetails userDetails,
                                    @PathVariable Long id, Model model) {
        Submission submission = submissionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        User currentUser = userDetails != null ? userDetails.getUser() : null;
        boolean canManageRelations = currentUser != null
                && (currentUser.getRole() == Role.LECTURER || currentUser.getRole() == Role.ADMIN);

        model.addAttribute("submission", submission);
        model.addAttribute("canAccessFile", submissionAccessService.canAccessSubmissionFile(currentUser, submission));
        model.addAttribute("timeline", auditService.getHistory(submission));
        model.addAttribute("relations", relationService.getRelationsFor(submission));
        model.addAttribute("canManageRelations", canManageRelations);

        if (canManageRelations) {
            model.addAttribute("relationTypes", RelationType.values());
            // A bounded pool of other recent submissions staff can link to.
            List<Submission> candidates = submissionRepository
                    .findAll(PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "createdAt")))
                    .stream()
                    .filter(s -> !s.getId().equals(submission.getId()))
                    .collect(Collectors.toList());
            model.addAttribute("relationCandidates", candidates);
        }
        return "student/project-detail";
    }

    @PreAuthorize("hasAnyRole('LECTURER','ADMIN')")
    @PostMapping("/projects/{id}/relations")
    public String addRelation(@AuthenticationPrincipal CustomUserDetails userDetails,
                              @PathVariable Long id,
                              @RequestParam Long targetSubmissionId,
                              @RequestParam RelationType relationType,
                              RedirectAttributes redirectAttributes) {
        User currentUser = userDetails != null ? userDetails.getUser() : null;
        try {
            relationService.createRelation(currentUser, id, targetSubmissionId, relationType);
            redirectAttributes.addFlashAttribute("successMessage", "Related project linked.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/projects/" + id;
    }

    @PreAuthorize("hasAnyRole('LECTURER','ADMIN')")
    @PostMapping("/projects/{id}/relations/{relationId}/delete")
    public String deleteRelation(@PathVariable Long id,
                                 @PathVariable Long relationId,
                                 RedirectAttributes redirectAttributes) {
        relationService.deleteRelation(relationId);
        redirectAttributes.addFlashAttribute("successMessage", "Link removed.");
        return "redirect:/projects/" + id;
    }
}
