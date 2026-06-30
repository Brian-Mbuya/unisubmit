package com.unisubmit.repository;

import com.unisubmit.domain.Department;
import com.unisubmit.domain.Faculty;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {
    List<Department> findByFaculty(Faculty faculty);
    List<Department> findByFacultyId(Long facultyId);

    /**
     * Eagerly loads the faculty association so Thymeleaf can access
     * d.faculty.name without triggering a lazy-load outside the session.
     */
    @EntityGraph(attributePaths = "faculty")
    @Query("SELECT d FROM Department d")
    List<Department> findAllWithFaculty();
}
