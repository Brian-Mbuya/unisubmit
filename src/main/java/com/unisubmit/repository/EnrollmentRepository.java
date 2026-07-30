package com.unisubmit.repository;

import com.unisubmit.domain.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    List<Enrollment> findByStudentId(Long studentProfileId);
    List<Enrollment> findByCurriculumId(Long curriculumId);
    Optional<Enrollment> findByStudentIdAndCurriculumId(Long studentProfileId, Long curriculumId);
}
