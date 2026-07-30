package com.unisubmit.repository;

import com.unisubmit.domain.TeachingAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TeachingAssignmentRepository extends JpaRepository<TeachingAssignment, Long> {
    List<TeachingAssignment> findByLecturerId(Long lecturerProfileId);
    List<TeachingAssignment> findByCurriculumId(Long curriculumId);
    Optional<TeachingAssignment> findByLecturerIdAndCurriculumId(Long lecturerProfileId, Long curriculumId);

    /** Batch-load assignments for multiple curriculum IDs in a single query (fixes N+1). */
    @Query("SELECT ta FROM TeachingAssignment ta WHERE ta.curriculum.id IN :ids")
    List<TeachingAssignment> findByCurriculumIdIn(@Param("ids") Collection<Long> ids);
}

