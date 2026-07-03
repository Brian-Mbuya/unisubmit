package com.unisubmit.controller;

import com.unisubmit.domain.StudentProfile;
import com.unisubmit.domain.User;
import com.unisubmit.dto.CollaborationOpportunity;
import com.unisubmit.repository.StudentProfileRepository;
import com.unisubmit.security.CustomUserDetails;
import com.unisubmit.service.CollaborationAssessmentService;
import com.unisubmit.service.CollaborationDiscoveryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 8 — the student-facing "Discover Collaborators" page.
 * Matches are grouped by collaboration type and shown viewer-relative.
 * Contact uses the existing collaboration-request flow (reuse, not a new table),
 * with the AI pitch prefilled so the recipient understands the approach.
 */
@Controller
@RequestMapping("/discover")
public class DiscoverController {

    private final CollaborationDiscoveryService discoveryService;
    private final CollaborationAssessmentService assessmentService;
    private final StudentProfileRepository studentProfileRepository;

    public DiscoverController(CollaborationDiscoveryService discoveryService,
                              CollaborationAssessmentService assessmentService,
                              StudentProfileRepository studentProfileRepository) {
        this.discoveryService = discoveryService;
        this.assessmentService = assessmentService;
        this.studentProfileRepository = studentProfileRepository;
    }

    @GetMapping
    public String discover(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        User viewer = userDetails.getUser();
        List<CollaborationOpportunity> opportunities = discoveryService.findOpportunitiesForStudent(viewer);

        // Group by collaboration type, preserving the value/score ordering.
        Map<String, List<CollaborationOpportunity>> grouped = new LinkedHashMap<>();
        for (CollaborationOpportunity o : opportunities) {
            grouped.computeIfAbsent(o.type().label(), k -> new java.util.ArrayList<>()).add(o);
        }

        model.addAttribute("grouped", grouped);
        model.addAttribute("totalCount", opportunities.size());
        model.addAttribute("aiConfigured", assessmentService.isConfigured());
        boolean discoverable = viewer.getStudentProfile() == null
                || viewer.getStudentProfile().isDiscoverableForCollaboration();
        model.addAttribute("discoverable", discoverable);
        return "discover";
    }

    @PostMapping("/visibility")
    @Transactional
    public String toggleVisibility(@AuthenticationPrincipal CustomUserDetails userDetails,
                                   @RequestParam(defaultValue = "false") boolean discoverable,
                                   RedirectAttributes ra) {
        StudentProfile profile = userDetails.getUser().getStudentProfile();
        if (profile != null) {
            StudentProfile managed = studentProfileRepository.findById(profile.getId()).orElse(profile);
            managed.setDiscoverableForCollaboration(discoverable);
            studentProfileRepository.save(managed);
            ra.addFlashAttribute("successMessage", discoverable
                    ? "Your projects are now discoverable for collaboration."
                    : "You have opted out of collaboration discovery. Matches will refresh shortly.");
        }
        return "redirect:/discover";
    }
}
