package com.unisubmit.repository;

import com.unisubmit.domain.CollaborationRequest;
import com.unisubmit.domain.CollaborationRequestStatus;
import com.unisubmit.domain.Submission;
import com.unisubmit.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * True when either side already asked and was turned down — drives the Discover cooldown
     * so a declined suggestion never resurfaces. Status is a bound parameter (never an inlined
     * enum literal in JPQL).
     */
    @Query("SELECT COUNT(r) > 0 FROM CollaborationRequest r WHERE r.status = :declined "
            + "AND ((r.submission = :candidate AND r.sender = :currentStudent) "
            + "OR (r.submission = :current AND r.sender = :candidateStudent))")
    boolean existsDeclinedBetween(@Param("current") Submission current,
                                  @Param("candidate") Submission candidate,
                                  @Param("currentStudent") User currentStudent,
                                  @Param("candidateStudent") User candidateStudent,
                                  @Param("declined") CollaborationRequestStatus declined);
}
