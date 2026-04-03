package com.chuka.irir.service;

import com.chuka.irir.dto.FeedbackDTO;
import com.chuka.irir.model.Project;
import com.chuka.irir.model.ProjectStatus;
import com.chuka.irir.model.Review;
import com.chuka.irir.model.ReviewDecision;
import com.chuka.irir.model.User;
import com.chuka.irir.model.Role;
import com.chuka.irir.repository.ProjectRepository;
import com.chuka.irir.repository.ReviewRepository;
import com.chuka.irir.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class FeedbackService {

    private final ReviewRepository reviewRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public FeedbackService(ReviewRepository reviewRepository,
                           ProjectRepository projectRepository,
                           UserRepository userRepository,
                           NotificationService notificationService) {
        this.reviewRepository = reviewRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public void submitFeedback(Long projectId, FeedbackDTO dto, Long supervisorId) {
        Objects.requireNonNull(dto, "Feedback is required.");
        if (dto.getAction() == null) {
            throw new IllegalArgumentException("A review decision is required.");
        }

        User supervisor = getSupervisor(supervisorId);
        Project project = getProjectForSupervisorReview(projectId, supervisorId);
        User student = project.getSubmittedBy();
        if (student == null) {
            throw new IllegalArgumentException("Submitted project is missing a student.");
        }
        
        Review review = Review.builder()
                .project(project)
                .reviewer(supervisor)
                .decision(mapDecision(dto.getAction()))
                .comments(dto.getComment())
                .build();
        reviewRepository.save(review);
        
        project.setSupervisor(supervisor);
        project.setStatus(mapStatus(dto.getAction()));
        
        if (dto.getAction() == FeedbackDTO.FeedbackAction.APPROVED) {
            notificationService.sendApprovalEmail(student, project);
        } else if (dto.getAction() == FeedbackDTO.FeedbackAction.FORWARDED_TO_INCUBATION) {
            notificationService.sendIncubationEmail(student, project);
        } else if (dto.getAction() == FeedbackDTO.FeedbackAction.REJECTED) {
            notificationService.sendRejectionEmail(student, project, dto.getComment());
        }

        projectRepository.save(project);
    }

    @Transactional(readOnly = true)
    public Project getProjectForSupervisorReview(Long projectId, Long supervisorId) {
        Project project = projectRepository.findDetailedForReviewById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
        User supervisor = getSupervisor(supervisorId);

        if (project.getStatus() == ProjectStatus.DRAFT) {
            throw new AccessDeniedException("Draft projects cannot be reviewed.");
        }

        User assignedSupervisor = project.getSupervisor();
        if (assignedSupervisor == null) {
            throw new AccessDeniedException("Projects without an assigned supervisor cannot be reviewed.");
        }
        if (!Objects.equals(assignedSupervisor.getId(), supervisor.getId())) {
            throw new AccessDeniedException("You are not allowed to review this project.");
        }

        return project;
    }

    private ProjectStatus mapStatus(FeedbackDTO.FeedbackAction action) {
        return switch (action) {
            case APPROVED -> ProjectStatus.APPROVED;
            case REJECTED -> ProjectStatus.REJECTED;
            case FORWARDED_TO_INCUBATION -> ProjectStatus.INCUBATION;
        };
    }

    private ReviewDecision mapDecision(FeedbackDTO.FeedbackAction action) {
        return switch (action) {
            case APPROVED, FORWARDED_TO_INCUBATION -> ReviewDecision.APPROVED;
            case REJECTED -> ReviewDecision.REJECTED;
        };
    }

    private User getSupervisor(Long supervisorId) {
        User supervisor = userRepository.findById(supervisorId)
                .orElseThrow(() -> new IllegalArgumentException("Supervisor not found"));
        if (supervisor.getRoles() == null || !supervisor.getRoles().contains(Role.SUPERVISOR)) {
            throw new AccessDeniedException("Only supervisors can review projects.");
        }
        return supervisor;
    }
}
