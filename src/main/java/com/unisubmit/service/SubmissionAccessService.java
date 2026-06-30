package com.unisubmit.service;

import com.unisubmit.domain.Role;
import com.unisubmit.domain.Submission;
import com.unisubmit.domain.User;
import com.unisubmit.domain.TeachingAssignment;
import com.unisubmit.repository.CollaborationRepository;
import com.unisubmit.repository.TeachingAssignmentRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SubmissionAccessService {

    private final CollaborationRepository collaborationRepository;
    private final TeachingAssignmentRepository teachingAssignmentRepository;

    public SubmissionAccessService(CollaborationRepository collaborationRepository,
                                   TeachingAssignmentRepository teachingAssignmentRepository) {
        this.collaborationRepository = collaborationRepository;
        this.teachingAssignmentRepository = teachingAssignmentRepository;
    }

    public boolean canAccessSubmissionFile(User user, Submission submission) {
        if (user == null || submission == null) {
            return false;
        }

        if (user.getRole() == Role.ADMIN) {
            return true;
        }

        // Submission owner always has access
        if (submission.getStudent().getId().equals(user.getId())) {
            return true;
        }

        // Group members have access to their group's submission files
        if (submission.getProjectGroup() != null) {
            boolean isMember = submission.getProjectGroup().getMembers().stream()
                    .anyMatch(m -> m.getId().equals(user.getId()));
            if (isMember) return true;
        }

        // Check if user is a lecturer assigned to this curriculum
        if (user.getRole() == Role.LECTURER && user.getLecturerProfile() != null
                && submission.getCurriculum() != null) {
            List<TeachingAssignment> assignments = teachingAssignmentRepository
                    .findByCurriculumId(submission.getCurriculum().getId());
            boolean assignedLecturer = assignments.stream()
                    .anyMatch(assignment ->
                            assignment.getLecturer().getId().equals(user.getLecturerProfile().getId())
                            && "ACTIVE".equals(assignment.getStatus()));
            if (assignedLecturer) {
                return true;
            }
        }

        return collaborationRepository.existsByUserAndSubmission(user, submission);
    }
}
