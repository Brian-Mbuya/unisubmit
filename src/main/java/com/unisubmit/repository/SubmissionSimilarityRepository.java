package com.unisubmit.repository;

import com.unisubmit.domain.Submission;
import com.unisubmit.domain.SubmissionSimilarity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubmissionSimilarityRepository extends JpaRepository<SubmissionSimilarity, Long> {

    @Query("SELECT s FROM SubmissionSimilarity s WHERE s.submissionA = :sub OR s.submissionB = :sub ORDER BY s.similarityScore DESC")
    List<SubmissionSimilarity> findBySubmissionOrderBySimilarityScoreDesc(Submission sub);
    
    @Query("SELECT s FROM SubmissionSimilarity s WHERE (s.submissionA = :a AND s.submissionB = :b) OR (s.submissionA = :b AND s.submissionB = :a)")
    Optional<SubmissionSimilarity> findBySubmissions(Submission a, Submission b);

    long countBySimilarityScoreGreaterThanEqual(Double threshold);

    /**
     * Ids of submissions (within the given set) that have at least one near-duplicate partner
     * at or above the threshold. One query for a whole lecturer queue — no N+1.
     */
    @Query("SELECT CASE WHEN s.submissionA.id IN :ids THEN s.submissionA.id ELSE s.submissionB.id END "
            + "FROM SubmissionSimilarity s "
            + "WHERE s.similarityScore >= :threshold "
            + "AND (s.submissionA.id IN :ids OR s.submissionB.id IN :ids)")
    List<Long> findFlaggedSubmissionIds(@Param("ids") Collection<Long> ids,
                                        @Param("threshold") Double threshold);

    @Modifying
    @Query("DELETE FROM SubmissionSimilarity s WHERE s.submissionA = :sub OR s.submissionB = :sub")
    void deleteBySubmission(Submission sub);
}
