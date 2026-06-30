package com.unisubmit.repository;

import com.unisubmit.domain.AcademicYear;
import com.unisubmit.domain.Semester;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SemesterRepository extends JpaRepository<Semester, Long> {
    List<Semester> findByAcademicYear(AcademicYear academicYear);
    List<Semester> findByAcademicYearId(Long academicYearId);
}
