package com.unisubmit.repository;

import com.unisubmit.domain.Submission;
import com.unisubmit.domain.User;
import com.unisubmit.domain.Curriculum;
import com.unisubmit.domain.Unit;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    List<Submission> findByStudent(User student);
    List<Submission> findByCurriculumIn(List<Curriculum> curricula);
    List<Submission> findByProjectGroupId(Long groupId);
    List<Submission> findByCurriculum_UnitAndStudentNot(Unit unit, User student);
    List<Submission> findByStudentNotOrderByCreatedAtDesc(User student, Pageable pageable);

    /** Batch-load submissions for multiple group IDs in a single query (fixes N+1). */
    @Query("SELECT s FROM Submission s WHERE s.projectGroup.id IN :groupIds")
    List<Submission> findByProjectGroupIdIn(@Param("groupIds") Collection<Long> groupIds);

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

