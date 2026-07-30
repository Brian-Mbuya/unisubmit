package com.unisubmit.repository;

import com.unisubmit.domain.Submission;
import com.unisubmit.domain.User;
import com.unisubmit.domain.Curriculum;
import com.unisubmit.domain.Unit;
import com.unisubmit.domain.SubmissionStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    long countByStatus(SubmissionStatus status);

    /** Ids of submissions whose insight has the given status — drives the embedding backfill. */
    @Query("select s.id from Submission s where s.aiInsight is not null and s.aiInsight.status = :status")
    List<Long> findIdsByInsightStatus(@Param("status") com.unisubmit.domain.AIInsightStatus status);

    List<Submission> findByStudent(User student);
    List<Submission> findByCurriculumIn(List<Curriculum> curricula);
    List<Submission> findByProjectGroupId(Long groupId);
    List<Submission> findByCurriculum_UnitAndStudentNot(Unit unit, User student);
    List<Submission> findByStudentNotOrderByCreatedAtDesc(User student, Pageable pageable);

    /** Batch-load submissions for multiple group IDs in a single query (fixes N+1). */
    @Query("SELECT s FROM Submission s WHERE s.projectGroup.id IN :groupIds")
    List<Submission> findByProjectGroupIdIn(@Param("groupIds") Collection<Long> groupIds);

    /**
     * Loads a recommendation candidate pool with its scoring-relevant
     * associations fetch-joined in one query, instead of ~3 lazy loads per
     * candidate inside RecommendationService.precomputeForSubmission.
     * (Safe to fetch-join both collections: they are Sets, not bags.)
     */
    @Query("SELECT DISTINCT s FROM Submission s " +
           "LEFT JOIN FETCH s.aiInsight " +
           "LEFT JOIN FETCH s.technologies " +
           "LEFT JOIN FETCH s.researchAreas " +
           "WHERE s.id IN :ids")
    List<Submission> findWithRecommendationDataByIdIn(@Param("ids") Collection<Long> ids);

    @Query("SELECT s FROM Submission s JOIN s.technologies t WHERE t.id = :techId")
    List<Submission> findByTechnologyId(@Param("techId") Long techId);

    @Query("SELECT s FROM Submission s JOIN s.frameworks f WHERE f.id = :frameworkId")
    List<Submission> findByFrameworkId(@Param("frameworkId") Long frameworkId);

    @Query("SELECT s FROM Submission s JOIN s.databases d WHERE d.id = :databaseId")
    List<Submission> findByDatabaseId(@Param("databaseId") Long databaseId);

    @Query("SELECT s FROM Submission s JOIN s.programmingLanguages p WHERE p.id = :langId")
    List<Submission> findByProgrammingLanguageId(@Param("langId") Long langId);

    @Query("SELECT s FROM Submission s JOIN s.researchAreas r WHERE r.id = :raId")
    List<Submission> findByResearchAreaId(@Param("raId") Long raId);

    @Query("SELECT s FROM Submission s JOIN s.skills sk WHERE sk.id = :skillId")
    List<Submission> findBySkillId(@Param("skillId") Long skillId);
}

