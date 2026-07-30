package com.unisubmit.service;

import com.unisubmit.domain.Collaboration;
import com.unisubmit.domain.CollaborationRequest;
import com.unisubmit.domain.CollaborationRequestStatus;
import com.unisubmit.domain.Role;
import com.unisubmit.domain.Submission;
import com.unisubmit.domain.User;
import com.unisubmit.repository.CollaborationRepository;
import com.unisubmit.repository.CollaborationRequestRepository;
import com.unisubmit.repository.SubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollaborationRequestServiceTest {

    @Mock
    private CollaborationRepository collaborationRepository;

    @Mock
    private CollaborationRequestRepository collaborationRequestRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    private CollaborationRequestService collaborationRequestService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        collaborationRequestService = new CollaborationRequestService(
                collaborationRepository,
                collaborationRequestRepository,
                submissionRepository
        );
    }

    @Test
    void acceptRequestCreatesCollaborationWithStableUserOrdering() {
        User sender = student(7L, "sender");
        User recipient = student(3L, "recipient");
        Submission submission = new Submission();
        submission.setId(12L);

        CollaborationRequest request = new CollaborationRequest();
        request.setId(99L);
        request.setSender(sender);
        request.setRecipient(recipient);
        request.setSubmission(submission);
        request.setStatus(CollaborationRequestStatus.PENDING);

        when(collaborationRequestRepository.findById(99L)).thenReturn(Optional.of(request));
        when(collaborationRepository.existsCollaboration(sender, recipient, submission)).thenReturn(false);

        collaborationRequestService.acceptRequest(recipient, 99L);

        assertEquals(CollaborationRequestStatus.ACCEPTED, request.getStatus());

        ArgumentCaptor<Collaboration> collaborationCaptor = ArgumentCaptor.forClass(Collaboration.class);
        verify(collaborationRepository).save(collaborationCaptor.capture());

        Collaboration saved = collaborationCaptor.getValue();
        assertEquals(recipient.getId(), saved.getUser1().getId());
        assertEquals(sender.getId(), saved.getUser2().getId());
        assertEquals(submission.getId(), saved.getSubmission().getId());
    }

    @Test
    void createRequestRejectsAlreadyActiveCollaboration() {
        User sender = student(2L, "sender");
        User recipient = student(5L, "recipient");
        Submission submission = new Submission();
        submission.setId(44L);
        submission.setStudent(recipient);

        when(submissionRepository.findById(44L)).thenReturn(Optional.of(submission));
        when(collaborationRepository.existsCollaboration(sender, recipient, submission)).thenReturn(true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> collaborationRequestService.createRequest(sender, 44L, "hello")
        );

        assertEquals("You are already collaborating on this project", exception.getMessage());
        verify(collaborationRequestRepository, never()).save(any());
    }

    private User student(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setName(username);
        user.setRole(Role.STUDENT);
        return user;
    }
}
