package com.unisubmit.service;

import com.unisubmit.domain.Curriculum;
import com.unisubmit.domain.LecturerProfile;
import com.unisubmit.domain.ProjectGroup;
import com.unisubmit.domain.Role;
import com.unisubmit.domain.Submission;
import com.unisubmit.domain.SubmissionStatus;
import com.unisubmit.domain.TeachingAssignment;
import com.unisubmit.domain.User;
import com.unisubmit.repository.CollaborationRepository;
import com.unisubmit.repository.TeachingAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Visibility matrix for {@link SubmissionAccessService}: who can read a file, who can
 * merely discover it, and — the point of 2.5 — which of those grants is "collaborator
 * only" so the UI hint never lies to owners or staff.
 */
class SubmissionAccessServiceTest {

    @Mock private CollaborationRepository collaborationRepository;
    @Mock private TeachingAssignmentRepository teachingAssignmentRepository;

    private SubmissionAccessService service;

    private User admin, owner, groupMember, assignedLecturer, collaborator, outsider;
    private Submission submission;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new SubmissionAccessService(collaborationRepository, teachingAssignmentRepository);

        admin = user(1L, Role.ADMIN, null);
        owner = user(2L, Role.STUDENT, null);
        groupMember = user(3L, Role.STUDENT, null);
        collaborator = user(4L, Role.STUDENT, null);
        outsider = user(5L, Role.STUDENT, null);

        LecturerProfile lecturerProfile = new LecturerProfile();
        lecturerProfile.setId(30L);
        assignedLecturer = user(6L, Role.LECTURER, lecturerProfile);

        Curriculum curriculum = new Curriculum();
        curriculum.setId(20L);

        ProjectGroup group = new ProjectGroup();
        group.setLeader(owner);
        group.getMembers().add(owner);
        group.getMembers().add(groupMember);

        submission = new Submission();
        submission.setId(100L);
        submission.setStudent(owner);
        submission.setCurriculum(curriculum);
        submission.setProjectGroup(group);
        submission.setStatus(SubmissionStatus.SUBMITTED);

        TeachingAssignment assignment = new TeachingAssignment();
        assignment.setLecturer(lecturerProfile);
        assignment.setStatus("ACTIVE");
        // Both canAccess and isCollaboratorOnly consult this for the lecturer branch.
        lenient().when(teachingAssignmentRepository.findByCurriculumId(20L))
                .thenReturn(List.of(assignment));

        // Only the designated collaborator has an accepted collaboration.
        lenient().when(collaborationRepository.existsByUserAndSubmission(collaborator, submission))
                .thenReturn(true);
    }

    @Test
    void ownerGroupStaffCanAccessButAreNotCollaboratorOnly() {
        for (User u : List.of(admin, owner, groupMember, assignedLecturer)) {
            assertTrue(service.canAccessSubmissionFile(u, submission), "should access: " + u.getId());
            assertFalse(service.isCollaboratorOnlyFileAccess(u, submission),
                    "must not be flagged collaborator-only: " + u.getId());
        }
    }

    @Test
    void collaboratorCanAccessAndIsFlaggedCollaboratorOnly() {
        assertTrue(service.canAccessSubmissionFile(collaborator, submission));
        assertTrue(service.isCollaboratorOnlyFileAccess(collaborator, submission),
                "an accepted-collaboration-only grant is exactly what the hint should announce");
    }

    @Test
    void outsiderCanNeitherAccessNorIsCollaboratorOnly() {
        assertFalse(service.canAccessSubmissionFile(outsider, submission));
        assertFalse(service.isCollaboratorOnlyFileAccess(outsider, submission));
    }

    @Test
    void peersCanDiscoverPutForwardWorkButNotPrivateDrafts() {
        submission.setStatus(SubmissionStatus.DRAFT);
        assertFalse(service.canDiscoverSubmission(outsider, submission), "drafts must not leak");

        submission.setStatus(SubmissionStatus.SUBMITTED);
        assertTrue(service.canDiscoverSubmission(outsider, submission), "submitted work is discoverable");
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    private User user(Long id, Role role, LecturerProfile lecturerProfile) {
        User u = new User();
        u.setId(id);
        u.setName("User " + id);
        u.setRole(role);
        u.setLecturerProfile(lecturerProfile);
        return u;
    }
}
