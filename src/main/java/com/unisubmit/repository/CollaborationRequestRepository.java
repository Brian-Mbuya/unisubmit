package com.unisubmit.repository;

import com.unisubmit.domain.CollaborationRequest;
import com.unisubmit.domain.CollaborationRequestStatus;
import com.unisubmit.domain.Submission;
import com.unisubmit.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CollaborationRequestRepository extends JpaRepository<CollaborationRequest, Long> {

    List<CollaborationRequest> findByRecipientOrderByCreatedAtDesc(User recipient);

    List<CollaborationRequest> findBySenderOrderByCreatedAtDesc(User sender);

    Optional<CollaborationRequest> findBySubmissionAndSenderAndRecipient(Submission submission, User sender, User recipient);

    long countByRecipientAndStatus(User recipient, CollaborationRequestStatus status);

    /** Evaluation-harness ground truth: accepted requests prove relevance. */
    List<CollaborationRequest> findByStatus(CollaborationRequestStatus status);

    boolean existsBySenderOrRecipient(User sender, User recipient);
}
