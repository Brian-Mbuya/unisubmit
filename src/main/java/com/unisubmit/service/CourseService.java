package com.unisubmit.service;

import com.unisubmit.domain.Course;
import com.unisubmit.domain.Department;
import com.unisubmit.repository.CourseRepository;
import com.unisubmit.repository.DepartmentRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CourseService {

    private final CourseRepository courseRepository;
    private final DepartmentRepository departmentRepository;

    public CourseService(CourseRepository courseRepository, DepartmentRepository departmentRepository) {
        this.courseRepository = courseRepository;
        this.departmentRepository = departmentRepository;
    }

    public List<Course> findAll() {
        return courseRepository.findAllWithDepartmentAndFaculty();
    }

    public List<Course> findByDepartment(Long departmentId) {
        return courseRepository.findByDepartmentId(departmentId);
    }

    public Course createCourse(Long departmentId, String name, String code) {
        Department dept = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new RuntimeException("Department not found"));
        Course course = new Course();
        course.setDepartment(dept);
        course.setName(name);
        course.setCode(code);
        return courseRepository.save(course);
    }

    public Course updateCourse(Long courseId, Long departmentId, String name, String code) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));
        if (departmentId != null && !departmentId.equals(course.getDepartment().getId())) {
            Department dept = departmentRepository.findById(departmentId)
                    .orElseThrow(() -> new RuntimeException("Department not found"));
            course.setDepartment(dept);
        }
        course.setName(name);
        course.setCode(code);
        return courseRepository.save(course);
    }

    public void deleteCourse(Long courseId) {
        courseRepository.deleteById(courseId);
    }

    public String courseNameById(Long courseId) {
        if (courseId == null) return null;
        return courseRepository.findById(courseId).map(Course::getName).orElse(null);
    }
}
