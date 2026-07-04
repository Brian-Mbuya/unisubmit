package com.unisubmit.controller;

import com.unisubmit.domain.StudentProfile;
import com.unisubmit.repository.StudentProfileRepository;
import com.unisubmit.security.CustomUserDetails;
import com.unisubmit.service.CollaborationAssessmentService;
import com.unisubmit.service.CollaborationDiscoveryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Phase 8 — legacy /discover endpoints.
 * The main page is now handled by ExploreController at /explore?tab=discover.
 * This controller is kept only for the visibility toggle endpoint.
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

    // NOTE: GET /discover is now handled as a redirect in ExploreController.
    // POST /discover/visibility is also handled there.
    // This class is kept to avoid breaking any internal service references.
}
