package com.chuka.irir.controller;

import com.chuka.irir.dto.FeedbackDTO;
import com.chuka.irir.exception.ResourceNotFoundException;
import com.chuka.irir.model.Project;
import com.chuka.irir.model.ProjectFile;
import com.chuka.irir.model.ProjectStatus;
import com.chuka.irir.model.User;
import com.chuka.irir.repository.ProjectRepository;
import com.chuka.irir.repository.ReviewRepository;
import com.chuka.irir.repository.UserRepository;
import com.chuka.irir.service.FileStorageService;
import com.chuka.irir.service.FeedbackService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Controller
@RequestMapping("/supervisor")
public class SupervisorController {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;
    private final FeedbackService feedbackService;
    private final FileStorageService fileStorageService;

    public SupervisorController(ProjectRepository projectRepository,
                                UserRepository userRepository,
                                ReviewRepository reviewRepository,
                                FeedbackService feedbackService,
                                FileStorageService fileStorageService) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.reviewRepository = reviewRepository;
        this.feedbackService = feedbackService;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model, Authentication authentication) {
        User supervisor = getSupervisor(authentication);
        List<Project> assignedProjects = projectRepository.findDashboardProjectsBySupervisor(supervisor);
        List<Project> pendingReview = projectRepository.findDashboardProjectsBySupervisorAndStatus(supervisor,
                ProjectStatus.SUBMITTED);

        model.addAttribute("user", supervisor);
        model.addAttribute("assignedProjects", assignedProjects);
        model.addAttribute("pendingReview", pendingReview);
        model.addAttribute("pageTitle", "Lecturer Dashboard");
        return "dashboard/supervisor";
    }

    @GetMapping("/review/{id}")
    public String viewProjectDetail(@PathVariable Long id, Model model, Authentication authentication) {
        User supervisor = getSupervisor(authentication);
        Project project = feedbackService.getProjectForSupervisorReview(id, supervisor.getId());
        model.addAttribute("project", project);
        model.addAttribute("user", supervisor);
        model.addAttribute("feedbackDTO", new FeedbackDTO());
        model.addAttribute("reviews", reviewRepository.findByProjectIdOrderByReviewedAtDesc(id));
        model.addAttribute("pageTitle", "Review Project");
        return "supervisor/review";
    }

    @PostMapping("/review/{id}")
    public String reviewProject(@PathVariable Long id,
                                @ModelAttribute FeedbackDTO dto,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        User supervisor = getSupervisor(authentication);
        feedbackService.submitFeedback(id, dto, supervisor.getId());
        redirectAttributes.addFlashAttribute("message", "Feedback submitted successfully.");
        return "redirect:/supervisor/dashboard";
    }

    @GetMapping("/review/{id}/files/{fileId}")
    public ResponseEntity<Resource> downloadAssignedFile(@PathVariable Long id,
                                                         @PathVariable Long fileId,
                                                         Authentication authentication) {
        User supervisor = getSupervisor(authentication);
        Project project = feedbackService.getProjectForSupervisorReview(id, supervisor.getId());

        List<ProjectFile> files = project.getFiles() == null ? List.of() : project.getFiles();
        Optional<ProjectFile> fileOpt = files.stream()
                .filter(file -> Objects.equals(file.getId(), fileId))
                .findFirst();

        ProjectFile projectFile = fileOpt
                .orElseThrow(() -> new ResourceNotFoundException("ProjectFile", "id", fileId));

        Resource resource = fileStorageService.loadAsResource(projectFile.getStoragePath());

        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (projectFile.getFileType() != null && !projectFile.getFileType().isBlank()) {
            try {
                mediaType = MediaType.parseMediaType(projectFile.getFileType());
            } catch (Exception ignored) {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + safeFileName(projectFile.getFileName()) + "\"")
                .body(resource);
    }

    @GetMapping("/reviews")
    public String reviewsRedirect() {
        return "redirect:/supervisor/dashboard";
    }

    private User getSupervisor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ResourceNotFoundException("User", "authentication", "current session");
        }
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    private String safeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "download";
        }
        return fileName.replace("\"", "");
    }
}
