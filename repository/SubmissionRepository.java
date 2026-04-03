package com.chuka.irir.repository;

import com.chuka.irir.model.Submission;
import com.chuka.irir.model.SubmissionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    @EntityGraph(attributePaths = {"student", "lecturer", "department", "course", "course.department", "unit", "finalYearProject"})
    List<Submission> findByStudentIdOrderBySubmittedAtDesc(Long studentId);

    @EntityGraph(attributePaths = {"student", "lecturer", "department", "course", "course.department", "unit", "finalYearProject"})
    List<Submission> findByLecturerIdOrderBySubmittedAtDesc(Long lecturerId);

    @EntityGraph(attributePaths = {"student", "lecturer", "department", "course", "course.department", "unit", "finalYearProject"})
    List<Submission> findByLecturerIdAndUnitIdOrderBySubmittedAtDesc(Long lecturerId, Long unitId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Submission> findWithLockById(Long id);

    List<Submission> findByLecturerIdAndTypeOrderBySubmittedAtDesc(Long lecturerId, SubmissionType type);

    List<Submission> findByTypeOrderBySubmittedAtDesc(SubmissionType type);

    List<Submission> findByFinalYearProjectIdOrderBySubmittedAtDesc(Long projectId);
}
