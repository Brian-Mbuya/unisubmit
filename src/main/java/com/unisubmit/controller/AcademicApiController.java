package com.unisubmit.controller;

import com.unisubmit.service.AcademicHierarchyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API endpoints consumed by the cascading Faculty → Department → Unit
 * selector on the admin create-user, new-submission, and registration forms.
 */
@RestController
@RequestMapping("/api/academic")
public class AcademicApiController {

    private final AcademicHierarchyService hierarchyService;
    private final com.unisubmit.repository.CourseRepository courseRepository;

    public AcademicApiController(AcademicHierarchyService hierarchyService,
                                 com.unisubmit.repository.CourseRepository courseRepository) {
        this.hierarchyService = hierarchyService;
        this.courseRepository = courseRepository;
    }

    @GetMapping("/programmes")
    public ResponseEntity<List<Map<String, Object>>> getProgrammesByDepartment(
            @RequestParam Long departmentId) {
        List<Map<String, Object>> result = courseRepository.findByDepartmentId(departmentId).stream()
                .map(p -> {
                    java.util.Map<String, Object> map = new java.util.HashMap<>();
                    map.put("id", p.getId());
                    map.put("name", p.getName() != null ? p.getName() : "");
                    map.put("code", p.getCode() != null ? p.getCode() : "");
                    return map;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/faculties")
    public ResponseEntity<List<Map<String, Object>>> getAllFaculties() {
        List<Map<String, Object>> result = hierarchyService.findAllFaculties().stream()
                .map(f -> {
                    java.util.Map<String, Object> map = new java.util.HashMap<>();
                    map.put("id", f.getId());
                    map.put("name", f.getName() != null ? f.getName() : "");
                    map.put("code", f.getCode() != null ? f.getCode() : "");
                    return map;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/departments")
    public ResponseEntity<List<Map<String, Object>>> getDepartmentsByFaculty(
            @RequestParam Long facultyId) {
        List<Map<String, Object>> result = hierarchyService.findDepartmentsByFaculty(facultyId).stream()
                .map(d -> {
                    java.util.Map<String, Object> map = new java.util.HashMap<>();
                    map.put("id", d.getId());
                    map.put("name", d.getName() != null ? d.getName() : "");
                    map.put("code", d.getCode() != null ? d.getCode() : "");
                    return map;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/units")
    public ResponseEntity<List<Map<String, Object>>> getUnitsByDepartment(
            @RequestParam Long departmentId) {
        List<Map<String, Object>> result = hierarchyService.findUnitsByDepartment(departmentId).stream()
                .map(u -> {
                    java.util.Map<String, Object> map = new java.util.HashMap<>();
                    map.put("id", u.getId());
                    map.put("name", u.getUnitName() != null ? u.getUnitName() : "");
                    return map;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }
}
