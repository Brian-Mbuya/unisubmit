package com.unisubmit.repository;

import com.unisubmit.domain.RelationType;
import com.unisubmit.domain.Submission;
import com.unisubmit.domain.SubmissionRelation;
import com.unisubmit.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SubmissionRelationRepository extends JpaRepository<SubmissionRelation, Long> {

    @Query("SELECT r FROM SubmissionRelation r WHERE r.submissionA = :sub OR r.submissionB = :sub ORDER BY r.createdAt DESC")
    List<SubmissionRelation> findBySubmission(Submission sub);

    boolean existsBySubmissionAAndSubmissionBAndRelationType(Submission a, Submission b, RelationType type);

    /** Rate-limiting support: how many relations a user has created since the given instant. */
    long countByCreatedByAndCreatedAtAfter(User createdBy, LocalDateTime since);
}
