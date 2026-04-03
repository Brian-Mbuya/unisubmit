package com.chuka.irir.service;

import com.chuka.irir.dto.CourseCreateDto;
import com.chuka.irir.dto.DepartmentCreateDto;
import com.chuka.irir.dto.AdminCourseOptionDto;
import com.chuka.irir.dto.AdminUnitSummaryDto;
import com.chuka.irir.dto.LecturerUnitAssignmentDto;
import com.chuka.irir.dto.UnitOptionDto;
import com.chuka.irir.dto.UnitCreateDto;
import com.chuka.irir.exception.ResourceNotFoundException;
import com.chuka.irir.model.Course;
import com.chuka.irir.model.Department;
import com.chuka.irir.model.LecturerUnitAssignment;
import com.chuka.irir.model.Role;
import com.chuka.irir.model.Unit;
import com.chuka.irir.model.User;
import com.chuka.irir.repository.CourseRepository;
import com.chuka.irir.repository.DepartmentRepository;
import com.chuka.irir.repository.LecturerUnitAssignmentRepository;
import com.chuka.irir.repository.UnitRepository;
import com.chuka.irir.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class AcademicStructureService {

    private final DepartmentRepository departmentRepository;
    private final CourseRepository courseRepository;
    private final UnitRepository unitRepository;
    private final LecturerUnitAssignmentRepository lecturerUnitAssignmentRepository;
    private final UserRepository userRepository;

    public AcademicStructureService(DepartmentRepository departmentRepository,
                                    CourseRepository courseRepository,
                                    UnitRepository unitRepository,
                                    LecturerUnitAssignmentRepository lecturerUnitAssignmentRepository,
                                    UserRepository userRepository) {
        this.departmentRepository = departmentRepository;
        this.courseRepository = courseRepository;
        this.unitRepository = unitRepository;
        this.lecturerUnitAssignmentRepository = lecturerUnitAssignmentRepository;
        this.userRepository = userRepository;
    }

    public Department createDepartment(DepartmentCreateDto dto) {
        Department department = Department.builder()
                .code(dto.getCode().trim().toUpperCase())
                .name(dto.getName().trim())
                .build();
        return departmentRepository.save(department);
    }

    public Course createCourse(CourseCreateDto dto) {
        Department department = departmentRepository.findById(dto.getDepartmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Department", "id", dto.getDepartmentId()));
        Course course = Course.builder()
                .code(dto.getCode().trim().toUpperCase())
                .name(dto.getName().trim())
                .department(department)
                .build();
        return courseRepository.save(course);
    }

    public Unit createUnit(UnitCreateDto dto) {
        Course course = courseRepository.findById(dto.getCourseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", dto.getCourseId()));
        Unit unit = Unit.builder()
                .code(dto.getCode().trim().toUpperCase())
                .name(dto.getName().trim())
                .course(course)
                .build();
        return unitRepository.save(unit);
    }

    public LecturerUnitAssignment assignLecturerToUnit(Long lecturerId, LecturerUnitAssignmentDto dto) {
        User lecturer = userRepository.findById(lecturerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", lecturerId));
        if (lecturer.getRoles() == null || !lecturer.getRoles().contains(Role.SUPERVISOR)) {
            throw new IllegalArgumentException("Only lecturers with the SUPERVISOR role can be assigned to units.");
        }

        Department department = departmentRepository.findById(dto.getDepartmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Department", "id", dto.getDepartmentId()));
        Course course = courseRepository.findById(dto.getCourseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", dto.getCourseId()));
        Unit unit = unitRepository.findById(dto.getUnitId())
                .orElseThrow(() -> new ResourceNotFoundException("Unit", "id", dto.getUnitId()));

        if (!course.getDepartment().getId().equals(department.getId())) {
            throw new IllegalArgumentException("The selected course does not belong to the chosen department.");
        }
        if (!unit.getCourse().getId().equals(course.getId())) {
            throw new IllegalArgumentException("The selected unit does not belong to the chosen course.");
        }

        return assignLecturerToResolvedUnit(lecturer, department, course, unit);
    }

    public List<LecturerUnitAssignment> assignUnitsToLecturer(Long lecturerId, Set<Long> unitIds) {
        User lecturer = userRepository.findById(lecturerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", lecturerId));
        if (lecturer.getRoles() == null || !lecturer.getRoles().contains(Role.SUPERVISOR)) {
            throw new IllegalArgumentException("Only lecturers with the SUPERVISOR role can be assigned to units.");
        }

        if (unitIds == null || unitIds.isEmpty()) {
            throw new IllegalArgumentException("Select at least one unit.");
        }

        return unitIds.stream()
                .map(unitId -> {
                    Unit unit = unitRepository.findById(unitId)
                            .orElseThrow(() -> new ResourceNotFoundException("Unit", "id", unitId));
                    Course course = unit.getCourse();
                    Department department = course.getDepartment();
                    return assignLecturerToResolvedUnit(lecturer, department, course, unit);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Department> listDepartments() {
        return departmentRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Course> listCourses(Long departmentId) {
        return courseRepository.findByDepartmentIdOrderByNameAsc(departmentId);
    }

    @Transactional(readOnly = true)
    public List<AdminCourseOptionDto> listCourseOptions() {
        return courseRepository.findAll().stream()
                .sorted(Comparator.comparing(Course::getName, String.CASE_INSENSITIVE_ORDER))
                .map(AdminCourseOptionDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Unit> listUnits(Long courseId) {
        return unitRepository.findByCourseIdOrderByNameAsc(courseId);
    }

    @Transactional(readOnly = true)
    public List<AdminUnitSummaryDto> listUnitSummaries() {
        List<Unit> units = unitRepository.findAll().stream()
                .sorted(Comparator.comparing(
                        unit -> unit.getCourse().getName() + " " + unit.getName(),
                        String.CASE_INSENSITIVE_ORDER))
                .toList();
        Map<Long, LecturerUnitAssignment> assignments = getAssignmentsByUnitId(units);
        return units.stream()
                .map(unit -> {
                    LecturerUnitAssignment assignment = assignments.get(unit.getId());
                    return AdminUnitSummaryDto.from(
                            unit,
                            assignment == null ? null : assignment.getLecturer().getId(),
                            assignment == null ? null : assignment.getLecturer().getFullName()
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UnitOptionDto> listUnitsWithLecturer(Long courseId) {
        List<Unit> units = unitRepository.findByCourseIdOrderByNameAsc(courseId);
        Map<Long, LecturerUnitAssignment> assignments = getAssignmentsByUnitId(units);
        return units.stream()
                .map(unit -> {
                    LecturerUnitAssignment assignment = assignments.get(unit.getId());
                    return UnitOptionDto.from(
                            unit,
                            assignment == null ? null : assignment.getLecturer().getId(),
                            assignment == null ? null : assignment.getLecturer().getFullName()
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LecturerUnitAssignment> listAssignmentsForLecturer(Long lecturerId) {
        return lecturerUnitAssignmentRepository.findByLecturerIdOrderByDepartmentNameAscCourseNameAscUnitNameAsc(lecturerId);
    }

    @Transactional(readOnly = true)
    public List<Unit> listAssignedUnitsForLecturer(Long lecturerId) {
        return lecturerUnitAssignmentRepository.findByLecturerIdOrderByDepartmentNameAscCourseNameAscUnitNameAsc(lecturerId).stream()
                .map(LecturerUnitAssignment::getUnit)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private LecturerUnitAssignment assignLecturerToResolvedUnit(User lecturer,
                                                                Department department,
                                                                Course course,
                                                                Unit unit) {
        lecturerUnitAssignmentRepository.deleteByUnitId(unit.getId());
        return lecturerUnitAssignmentRepository.save(LecturerUnitAssignment.builder()
                .lecturer(lecturer)
                .department(department)
                .course(course)
                .unit(unit)
                .build());
    }

    @Transactional(readOnly = true)
    public User getAssignedLecturer(Unit unit) {
        if (unit == null || unit.getId() == null) {
            return null;
        }
        return lecturerUnitAssignmentRepository.findFirstByUnitId(unit.getId())
                .map(LecturerUnitAssignment::getLecturer)
                .orElse(null);
    }

    private Map<Long, LecturerUnitAssignment> getAssignmentsByUnitId(List<Unit> units) {
        Set<Long> unitIds = units.stream()
                .map(Unit::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (unitIds.isEmpty()) {
            return Map.of();
        }
        return lecturerUnitAssignmentRepository.findByUnitIdIn(unitIds).stream()
                .filter(assignment -> assignment.getUnit() != null && assignment.getUnit().getId() != null)
                .collect(Collectors.toMap(
                        assignment -> assignment.getUnit().getId(),
                        assignment -> assignment,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }
}
