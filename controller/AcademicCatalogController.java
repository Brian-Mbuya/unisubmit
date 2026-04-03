package com.chuka.irir.controller;

import com.chuka.irir.model.Course;
import com.chuka.irir.model.Department;
import com.chuka.irir.dto.UnitOptionDto;
import com.chuka.irir.service.AcademicStructureService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/academic")
public class AcademicCatalogController {

    private final AcademicStructureService academicStructureService;

    public AcademicCatalogController(AcademicStructureService academicStructureService) {
        this.academicStructureService = academicStructureService;
    }

    @GetMapping("/departments")
    public ResponseEntity<List<Department>> departments() {
        return ResponseEntity.ok(academicStructureService.listDepartments());
    }

    @GetMapping("/courses")
    public ResponseEntity<List<Course>> courses(@RequestParam Long departmentId) {
        return ResponseEntity.ok(academicStructureService.listCourses(departmentId));
    }

    @GetMapping("/units")
    public ResponseEntity<List<UnitOptionDto>> units(@RequestParam Long courseId) {
        return ResponseEntity.ok(academicStructureService.listUnitsWithLecturer(courseId));
    }
}
