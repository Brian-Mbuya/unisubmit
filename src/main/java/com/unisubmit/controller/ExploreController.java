package com.unisubmit.controller;

import com.unisubmit.domain.StudentProfile;
import com.unisubmit.domain.User;
import com.unisubmit.dto.CollaborationOpportunity;
import com.unisubmit.repository.StudentProfileRepository;
import com.unisubmit.security.CustomUserDetails;
import com.unisubmit.service.CollaborationAssessmentService;
import com.unisubmit.service.CollaborationDiscoveryService;
import com.unisubmit.service.SearchService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Unified Explore page — merges the Search (project archive) and Discover
 * (collaboration matching) pages into a single tabbed experience.
 *
 * The old /search and /discover routes are kept as redirects for backwards compatibility.
 */
@Controller
public class ExploreController {

    private final SearchService searchService;
    private final CollaborationDiscoveryService discoveryService;
    private final CollaborationAssessmentService assessmentService;
    private final StudentProfileRepository studentProfileRepository;

    public ExploreController(SearchService searchService,
                             CollaborationDiscoveryService discoveryService,
                             CollaborationAssessmentService assessmentService,
                             StudentProfileRepository studentProfileRepository) {
        this.searchService = searchService;
        this.discoveryService = discoveryService;
        this.assessmentService = assessmentService;
        this.studentProfileRepository = studentProfileRepository;
    }

    @GetMapping("/explore")
    public String explore(@AuthenticationPrincipal CustomUserDetails userDetails,
                          @RequestParam(required = false, defaultValue = "search") String tab,
                          @RequestParam(required = false) String q,
                          Model model) {

        User viewer = userDetails.getUser();

        // ── Search tab ─────────────────────────────────────────────────────
        String query = q == null ? "" : q.trim();
        List<SearchService.SearchHit> hits = query.isEmpty()
                ? List.of()
                : searchService.search(query, viewer);
        model.addAttribute("query", query);
        model.addAttribute("hits", hits);
        model.addAttribute("semanticEnabled", searchService.isSemanticEnabled());

        // ── Discover tab ────────────────────────────────────────────────────
        // Only students get collaboration matching; other roles see search only.
        if (viewer.getStudentProfile() != null) {
            List<CollaborationOpportunity> opportunities = discoveryService.findOpportunitiesForStudent(viewer);

            // Sort: HIGH → MEDIUM → SHORTLISTED, preserving insertion order within groups.
            Map<String, List<CollaborationOpportunity>> grouped = new LinkedHashMap<>();
            for (CollaborationOpportunity o : opportunities) {
                grouped.computeIfAbsent(o.type().label(), k -> new java.util.ArrayList<>()).add(o);
            }

            model.addAttribute("grouped", grouped);
            model.addAttribute("totalCount", opportunities.size());
            model.addAttribute("aiConfigured", assessmentService.isConfigured());

            boolean discoverable = viewer.getStudentProfile().isDiscoverableForCollaboration();
            model.addAttribute("discoverable", discoverable);
        } else {
            model.addAttribute("grouped", Map.of());
            model.addAttribute("totalCount", 0);
            model.addAttribute("aiConfigured", false);
            model.addAttribute("discoverable", false);
        }

        model.addAttribute("activeTab", tab);
        return "explore";
    }

    /** Redirect old /search URL to the new explore page. */
    @GetMapping("/search")
    public String searchRedirect(@RequestParam(required = false) String q) {
        return "redirect:/explore?tab=search" + (q != null && !q.isBlank() ? "&q=" + q : "");
    }

    /** Redirect old /discover URL to the new explore page. */
    @GetMapping("/discover")
    public String discoverRedirect() {
        return "redirect:/explore?tab=discover";
    }

    @PostMapping("/discover/visibility")
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
                    : "You have opted out of collaboration discovery.");
        }
        return "redirect:/explore?tab=discover";
    }
}
