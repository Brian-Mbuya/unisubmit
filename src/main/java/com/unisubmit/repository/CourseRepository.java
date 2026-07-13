package com.unisubmit.repository;

import com.unisubmit.domain.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {
    List<Course> findByDepartmentId(Long departmentId);

    /** Programme lookup by its short code (case-insensitive) — used by CSV import. */
    Optional<Course> findByCodeIgnoreCase(String code);

    @Query("SELECT c FROM Course c JOIN FETCH c.department d JOIN FETCH d.faculty ORDER BY c.name ASC")
    List<Course> findAllWithDepartmentAndFaculty();
}
