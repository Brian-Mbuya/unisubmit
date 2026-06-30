package com.unisubmit.service;

import com.unisubmit.domain.Department;
import com.unisubmit.domain.Faculty;
import com.unisubmit.domain.Unit;
import com.unisubmit.repository.DepartmentRepository;
import com.unisubmit.repository.FacultyRepository;
import com.unisubmit.repository.UnitRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AcademicHierarchyService {

    private final FacultyRepository facultyRepository;
    private final DepartmentRepository departmentRepository;
    private final UnitRepository unitRepository;

    public AcademicHierarchyService(FacultyRepository facultyRepository,
                                    DepartmentRepository departmentRepository,
                                    UnitRepository unitRepository) {
        this.facultyRepository = facultyRepository;
        this.departmentRepository = departmentRepository;
        this.unitRepository = unitRepository;
    }

    // ── Read: hierarchy traversal ─────────────────────────────────────────────

    public List<Faculty> findAllFaculties() {
        return facultyRepository.findAll();
    }

    public List<Department> findDepartmentsByFaculty(Long facultyId) {
        return departmentRepository.findByFacultyId(facultyId);
    }

    public List<Department> findAllDepartments() {
        return departmentRepository.findAllWithFaculty();
    }

    public List<Unit> findUnitsByDepartment(Long departmentId) {
        return unitRepository.findByDepartmentId(departmentId);
    }

    // ── Stats for overview cards ──────────────────────────────────────────────

    public long countFaculties()    { return facultyRepository.count(); }
    public long countDepartments()  { return departmentRepository.count(); }

    // ── Admin CRUD ────────────────────────────────────────────────────────────

    public Faculty createFaculty(String name, String code) {
        Faculty faculty = new Faculty();
        faculty.setName(name);
        faculty.setCode(code);
        return facultyRepository.save(faculty);
    }

    public Faculty updateFaculty(Long facultyId, String name, String code) {
        Faculty faculty = facultyRepository.findById(facultyId)
                .orElseThrow(() -> new RuntimeException("Faculty not found: " + facultyId));
        faculty.setName(name);
        faculty.setCode(code);
        return facultyRepository.save(faculty);
    }

    public void deleteFaculty(Long facultyId) {
        facultyRepository.deleteById(facultyId);
    }

    public Department createDepartment(Long facultyId, String name, String code) {
        Faculty faculty = facultyRepository.findById(facultyId)
                .orElseThrow(() -> new RuntimeException("Faculty not found: " + facultyId));
        Department dept = new Department();
        dept.setFaculty(faculty);
        dept.setName(name);
        dept.setCode(code);
        return departmentRepository.save(dept);
    }

    public Department updateDepartment(Long departmentId, Long facultyId, String name, String code) {
        Department dept = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new RuntimeException("Department not found: " + departmentId));
        if (facultyId != null) {
            Faculty faculty = facultyRepository.findById(facultyId)
                    .orElseThrow(() -> new RuntimeException("Faculty not found: " + facultyId));
            dept.setFaculty(faculty);
        }
        dept.setName(name);
        dept.setCode(code);
        return departmentRepository.save(dept);
    }

    public void deleteDepartment(Long departmentId) {
        departmentRepository.deleteById(departmentId);
    }

    public Department findDepartmentById(Long departmentId) {
        return departmentRepository.findById(departmentId)
                .orElseThrow(() -> new RuntimeException("Department not found: " + departmentId));
    }

    // ── Name resolvers (for Thymeleaf / view layer) ───────────────────────────

    /** Resolves a faculty id to its name, or null if not found / id is null. */
    public String facultyNameById(Long facultyId) {
        if (facultyId == null) return null;
        return facultyRepository.findById(facultyId).map(Faculty::getName).orElse(null);
    }

    /** Resolves a department id to its name, or null if not found / id is null. */
    public String departmentNameById(Long departmentId) {
        if (departmentId == null) return null;
        return departmentRepository.findById(departmentId).map(Department::getName).orElse(null);
    }

    /** Resolves a unit id to its name, or null if not found / id is null. */
    public String unitNameById(Long unitId) {
        if (unitId == null) return null;
        return unitRepository.findById(unitId).map(Unit::getUnitName).orElse(null);
    }
}
