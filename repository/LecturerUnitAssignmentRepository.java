package com.chuka.irir.repository;

import com.chuka.irir.model.LecturerUnitAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface LecturerUnitAssignmentRepository extends JpaRepository<LecturerUnitAssignment, Long> {

    List<LecturerUnitAssignment> findByLecturerIdOrderByDepartmentNameAscCourseNameAscUnitNameAsc(Long lecturerId);

    List<LecturerUnitAssignment> findByUnitIdOrderByLecturerFirstNameAscLecturerLastNameAsc(Long unitId);

    Optional<LecturerUnitAssignment> findFirstByUnitId(Long unitId);

    Optional<LecturerUnitAssignment> findByLecturerIdAndUnitId(Long lecturerId, Long unitId);

    List<LecturerUnitAssignment> findByUnitIdIn(Set<Long> unitIds);

    void deleteByUnitId(Long unitId);
}
