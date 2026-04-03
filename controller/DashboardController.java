package com.chuka.irir.controller;

import com.chuka.irir.exception.ResourceNotFoundException;
import com.chuka.irir.model.Role;
import com.chuka.irir.model.User;
import com.chuka.irir.model.ProjectStatus;
import com.chuka.irir.repository.ProjectRepository;
import com.chuka.irir.repository.SimilarityReportRepository;
import com.chuka.irir.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Set;

/**
 * Controller for role-based dashboard routing.
 *
 * After successful login, Spring Security redirects to /dashboard.
 * This controller inspects the authenticated user's primary role
 * and forwards them to the appropriate role-specific dashboard view.
 *
 * <p>Each dashboard displays relevant statistics and quick actions
 * tailored to the user's role.</p>
 */
@Controller
public class DashboardController {

    private static final Logger logger = LoggerFactory.getLogger(DashboardController.class);

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final SimilarityReportRepository similarityReportRepository;

    public DashboardController(UserRepository userRepository,
                               ProjectRepository projectRepository,
                               SimilarityReportRepository similarityReportRepository) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.similarityReportRepository = similarityReportRepository;
    }

    /**
     * Routes the authenticated user to their role-specific dashboard.
     *
     * Role priority (if user has multiple roles):
     * ADMIN > DIRECTORATE > SUPERVISOR > STUDENT
     *
     * @param authentication the current Spring Security authentication
     * @param model          the Spring MVC model
     * @return the dashboard view name for the user's primary role
     */
    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        String email = requireAuthenticatedEmail(authentication);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        model.addAttribute("user", user);

        // Determine roles
        Set<String> roles = AuthorityUtils.authorityListToSet(authentication.getAuthorities());
        logger.debug("Dashboard access by {} with roles: {}", email, roles);

        // Route to role-specific dashboard (priority: ADMIN > DIRECTORATE > SUPERVISOR > STUDENT)
        if (roles.contains("ROLE_ADMIN")) {
            return adminDashboard(model);
        } else if (roles.contains("ROLE_DIRECTORATE")) {
            return directorateDashboard(model);
        } else if (roles.contains("ROLE_SUPERVISOR")) {
            return supervisorDashboard(user, model);
        } else {
            return studentDashboard(user, model);
        }
    }

    // ==================== Role-Specific Dashboard Logic ====================

    /**
     * Prepares the student dashboard with their projects and stats.
     */
    private String studentDashboard(User user, Model model) {
        model.addAttribute("projects", projectRepository.findBySubmittedByOrderByCreatedAtDesc(user));
        model.addAttribute("pageTitle", "Student Dashboard");
        return "dashboard/student";
    }

    /**
     * Prepares the supervisor dashboard with assigned projects awaiting review.
     */
    private String supervisorDashboard(User user, Model model) {
        model.addAttribute("assignedProjects", projectRepository.findDashboardProjectsBySupervisor(user));
        model.addAttribute("pendingReview",
                projectRepository.findDashboardProjectsBySupervisorAndStatus(user, ProjectStatus.SUBMITTED));
        model.addAttribute("pageTitle", "Lecturer Dashboard");
        return "dashboard/supervisor";
    }

    /**
     * Prepares the directorate dashboard with analytics summary.
     */
    private String directorateDashboard(Model model) {
        model.addAttribute("totalProjects", projectRepository.countSubmittedProjects());
        model.addAttribute("approvedCount", projectRepository.countByStatus(ProjectStatus.APPROVED));
        model.addAttribute("pendingCount", projectRepository.countByStatus(ProjectStatus.SUBMITTED));
        model.addAttribute("flaggedCount", similarityReportRepository.countByFlaggedTrue());
        model.addAttribute("pageTitle", "Directorate Dashboard");
        return "dashboard/directorate";
    }

    /**
     * Prepares the admin dashboard with system-wide statistics.
     */
    private String adminDashboard(Model model) {
        model.addAttribute("totalUsers", userRepository.count());
        model.addAttribute("studentCount", userRepository.countByRoles(Role.STUDENT));
        model.addAttribute("supervisorCount", userRepository.countByRoles(Role.SUPERVISOR));
        model.addAttribute("directorateCount", userRepository.countByRoles(Role.DIRECTORATE));
        model.addAttribute("totalProjects", projectRepository.countSubmittedProjects());
        model.addAttribute("pageTitle", "Admin Dashboard");
        return "dashboard/admin";
    }

    private String requireAuthenticatedEmail(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ResourceNotFoundException("User", "authentication", "current session");
        }
        return authentication.getName();
    }
}
