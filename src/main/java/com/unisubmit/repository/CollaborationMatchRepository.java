package com.unisubmit.repository;

import com.unisubmit.domain.CollaborationMatch;
import com.unisubmit.domain.CollaborationValue;
import com.unisubmit.domain.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CollaborationMatchRepository extends JpaRepository<CollaborationMatch, Long> {

    /** All matches touching this submission (either side), best mechanical score first. */
    @Query("SELECT m FROM CollaborationMatch m WHERE m.submissionA = :sub OR m.submissionB = :sub "
            + "ORDER BY m.mechanicalScore DESC")
    List<CollaborationMatch> findBySubmission(@Param("sub") Submission sub);

    /** Assessed, worth-showing matches for a set of the viewer's own submissions. */
    @Query("SELECT m FROM CollaborationMatch m "
            + "WHERE (m.submissionA IN :subs OR m.submissionB IN :subs) "
            + "AND m.collaborationValue IN :values "
            + "ORDER BY m.collaborationValue ASC, m.mechanicalScore DESC")
    List<CollaborationMatch> findForSubmissionsWithValues(@Param("subs") List<Submission> subs,
                                                          @Param("values") List<CollaborationValue> values);

    @Query("SELECT m FROM CollaborationMatch m WHERE "
            + "(m.submissionA = :a AND m.submissionB = :b) OR (m.submissionA = :b AND m.submissionB = :a)")
    Optional<CollaborationMatch> findByPair(@Param("a") Submission a, @Param("b") Submission b);

    /** Stage 2 work queue: shortlisted pairs the LLM has not assessed yet. */
    List<CollaborationMatch> findByCollaborationValue(CollaborationValue value);

    long countByCollaborationValueIn(List<CollaborationValue> values);

    @Modifying
    @Query("DELETE FROM CollaborationMatch m WHERE m.submissionA = :sub OR m.submissionB = :sub")
    void deleteBySubmission(@Param("sub") Submission sub);
}
