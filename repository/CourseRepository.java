package com.chuka.irir.repository;

import com.chuka.irir.model.Course;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {

    List<Course> findByDepartmentIdOrderByNameAsc(Long departmentId);
}
