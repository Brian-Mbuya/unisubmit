package com.unisubmit.service;

import com.unisubmit.domain.Role;
import com.unisubmit.domain.Submission;
import com.unisubmit.domain.SubmissionStatus;
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

    /**
     * True when the ONLY thing granting this user file access is an accepted
     * collaboration — i.e. {@link #canAccessSubmissionFile} passes but the user is not
     * an admin, the owner, a group member, or an assigned lecturer. Drives the
     * "Unlocked because you are an accepted collaborator" hint so it never shows to the
     * owner/staff (2.5).
     */
    public boolean isCollaboratorOnlyFileAccess(User user, Submission submission) {
        if (user == null || submission == null) {
            return false;
        }
        if (!canAccessSubmissionFile(user, submission)) {
            return false;
        }
        if (user.getRole() == Role.ADMIN) {
            return false;
        }
        if (submission.getStudent().getId().equals(user.getId())) {
            return false;
        }
        if (submission.getProjectGroup() != null
                && submission.getProjectGroup().getMembers().stream()
                        .anyMatch(m -> m.getId().equals(user.getId()))) {
            return false;
        }
        if (user.getRole() == Role.LECTURER && user.getLecturerProfile() != null
                && submission.getCurriculum() != null) {
            List<TeachingAssignment> assignments = teachingAssignmentRepository
                    .findByCurriculumId(submission.getCurriculum().getId());
            boolean assignedLecturer = assignments.stream()
                    .anyMatch(assignment ->
                            assignment.getLecturer().getId().equals(user.getLecturerProfile().getId())
                            && "ACTIVE".equals(assignment.getStatus()));
            if (assignedLecturer) {
                return false;
            }
        }
        // canAccessSubmissionFile was true, but none of the above grants applied →
        // only the collaboration check could have returned true.
        return true;
    }

    /**
     * Whether a user may *discover* a submission — i.e. see its title, summary,
     * keywords and owner in similarity/recommendation results and on the shared
     * project page. Weaker than file access: discovery is the collaboration
     * feature's whole point, but private drafts must never leak into another
     * student's recommendations.
     */
    public boolean canDiscoverSubmission(User user, Submission submission) {
        if (user == null || submission == null) {
            return false;
        }

        // Owner / group members / staff / collaborators can always see it
        if (canAccessSubmissionFile(user, submission)) {
            return true;
        }
        if (user.getRole() == Role.LECTURER) {
            return true;
        }

        // Peers can only discover work that has actually been put forward
        SubmissionStatus status = submission.getStatus();
        return status != SubmissionStatus.DRAFT;
    }
}
