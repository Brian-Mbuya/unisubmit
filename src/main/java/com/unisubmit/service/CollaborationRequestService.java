package com.unisubmit.service;

import com.unisubmit.domain.Collaboration;
import com.unisubmit.domain.CollaborationRequest;
import com.unisubmit.domain.CollaborationRequestStatus;
import com.unisubmit.domain.Submission;
import com.unisubmit.domain.User;
import com.unisubmit.dto.CollaborationInboxView;
import com.unisubmit.repository.CollaborationRepository;
import com.unisubmit.repository.CollaborationRequestRepository;
import com.unisubmit.repository.SubmissionRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CollaborationRequestService {

    private final CollaborationRepository collaborationRepository;
    private final CollaborationRequestRepository collaborationRequestRepository;
    private final SubmissionRepository submissionRepository;

    public CollaborationRequestService(CollaborationRepository collaborationRepository,
                                       CollaborationRequestRepository collaborationRequestRepository,
                                       SubmissionRepository submissionRepository) {
        this.collaborationRepository = collaborationRepository;
        this.collaborationRequestRepository = collaborationRequestRepository;
        this.submissionRepository = submissionRepository;
    }

    @Transactional
    public void createRequest(User sender, Long targetSubmissionId, String message) {
        Submission targetSubmission = submissionRepository.findById(targetSubmissionId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        User recipient = targetSubmission.getStudent();
        if (recipient.getId().equals(sender.getId())) {
            throw new IllegalArgumentException("You cannot send a collaboration request to your own project");
        }
        if (collaborationRepository.existsCollaboration(sender, recipient, targetSubmission)) {
            throw new IllegalArgumentException("You are already collaborating on this project");
        }

        collaborationRequestRepository.findBySubmissionAndSenderAndRecipient(targetSubmission, sender, recipient)
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("You already sent a request for this project");
                });

        CollaborationRequest request = new CollaborationRequest();
        request.setSubmission(targetSubmission);
        request.setSender(sender);
        request.setRecipient(recipient);
        request.setMessage(normalizeMessage(message));
        request.setStatus(CollaborationRequestStatus.PENDING);

        collaborationRequestRepository.save(request);
    }

    @Transactional
    public void acceptRequest(User currentUser, Long requestId) {
        CollaborationRequest request = getRequestForRecipient(currentUser, requestId);
        request.setStatus(CollaborationRequestStatus.ACCEPTED);

        if (!collaborationRepository.existsCollaboration(request.getSender(), request.getRecipient(), request.getSubmission())) {
            Collaboration collaboration = new Collaboration();
            if (request.getSender().getId() < request.getRecipient().getId()) {
                collaboration.setUser1(request.getSender());
                collaboration.setUser2(request.getRecipient());
            } else {
                collaboration.setUser1(request.getRecipient());
                collaboration.setUser2(request.getSender());
            }
            collaboration.setSubmission(request.getSubmission());
            collaborationRepository.save(collaboration);
        }
    }

    @Transactional
    public void declineRequest(User currentUser, Long requestId) {
        CollaborationRequest request = getRequestForRecipient(currentUser, requestId);
        request.setStatus(CollaborationRequestStatus.DECLINED);
    }

    /**
     * Lightweight count for the header badge — a single COUNT query instead of
     * materialising the whole inbox view on every page load.
     */
    /**
     * True when either student already asked the other about this pair and was declined.
     * Discover uses this as a cooldown so a rejected suggestion never resurfaces.
     */
    public boolean wasDeclinedBetween(Submission current, Submission candidate,
                                      User currentStudent, User candidateStudent) {
        if (current == null || candidate == null || currentStudent == null || candidateStudent == null) {
            return false;
        }
        return collaborationRequestRepository.existsDeclinedBetween(current, candidate,
                currentStudent, candidateStudent,
                com.unisubmit.domain.CollaborationRequestStatus.DECLINED);
    }

    public long countPendingIncoming(User user) {
        return collaborationRequestRepository.countByRecipientAndStatus(user, CollaborationRequestStatus.PENDING);
    }

    public CollaborationInboxView getInbox(User user) {
        List<CollaborationRequest> incoming = collaborationRequestRepository.findByRecipientOrderByCreatedAtDesc(user);
        List<CollaborationRequest> outgoing = collaborationRequestRepository.findBySenderOrderByCreatedAtDesc(user);
        List<Collaboration> activeCollaborations = collaborationRepository.findByUserOrderByCreatedAtDesc(user);
        long pendingIncoming = collaborationRequestRepository.countByRecipientAndStatus(user, CollaborationRequestStatus.PENDING);

        return new CollaborationInboxView(incoming, outgoing, activeCollaborations, pendingIncoming);
    }

    public Map<Long, CollaborationRequestStatus> getRequestStatusesForSender(User sender, List<Submission> submissions) {
        Set<Long> submissionIds = submissions.stream().map(Submission::getId).collect(Collectors.toSet());

        return collaborationRequestRepository.findBySenderOrderByCreatedAtDesc(sender).stream()
                .filter(request -> submissionIds.contains(request.getSubmission().getId()))
                .collect(Collectors.toMap(
                        request -> request.getSubmission().getId(),
                        CollaborationRequest::getStatus,
                        (existing, replacement) -> existing
                ));
    }

    private CollaborationRequest getRequestForRecipient(User currentUser, Long requestId) {
        CollaborationRequest request = collaborationRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));

        if (!request.getRecipient().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("You cannot manage this request");
        }
        if (request.getStatus() != CollaborationRequestStatus.PENDING) {
            throw new IllegalArgumentException("This request has already been handled");
        }

        return request;
    }

    private String normalizeMessage(String message) {
        if (message == null) {
            return null;
        }

        String trimmed = message.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
    }
}
