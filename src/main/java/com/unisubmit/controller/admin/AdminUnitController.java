package com.unisubmit.controller.admin;

import com.unisubmit.domain.Unit;
import com.unisubmit.domain.Curriculum;
import com.unisubmit.repository.CourseRepository;
import com.unisubmit.repository.CurriculumRepository;
import com.unisubmit.repository.UnitRepository;
import com.unisubmit.service.AcademicHierarchyService;
import com.unisubmit.service.CourseService;
import com.unisubmit.service.UnitService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;

@Controller
@RequestMapping("/admin/units")
public class AdminUnitController {

    private final UnitService unitService;
    private final AcademicHierarchyService academicHierarchyService;
    private final CourseService courseService;
    private final CourseRepository courseRepository;
    private final CurriculumRepository curriculumRepository;
    private final UnitRepository unitRepository;

    public AdminUnitController(UnitService unitService,
                               AcademicHierarchyService academicHierarchyService,
                               CourseService courseService,
                               CourseRepository courseRepository,
                               CurriculumRepository curriculumRepository,
                               UnitRepository unitRepository) {
        this.unitService = unitService;
        this.academicHierarchyService = academicHierarchyService;
        this.courseService = courseService;
        this.courseRepository = courseRepository;
        this.curriculumRepository = curriculumRepository;
        this.unitRepository = unitRepository;
    }

    @GetMapping
    public String listUnits(Model model) {
        model.addAttribute("units", unitService.findAllUnits());
        model.addAttribute("departments", academicHierarchyService.findAllDepartments());
        model.addAttribute("courses", courseService.findAll());
        model.addAttribute("curricula", curriculumRepository.findAll());
        return "admin/units";
    }

    @PostMapping
    public String createUnit(@RequestParam String code,
                             @RequestParam String name,
                             @RequestParam Long departmentId,
                             @RequestParam(required = false) Long courseId,
                             @RequestParam(required = false) String submissionDeadline,
                             RedirectAttributes redirectAttributes) {
        try {
            Unit unit = unitService.createUnit(departmentId, code, name, 3);

            // Set optional deadline
            if (submissionDeadline != null && !submissionDeadline.isBlank()) {
                unit.setSubmissionDeadline(LocalDateTime.parse(submissionDeadline));
                unitRepository.save(unit);
            }

            if (courseId != null) {
                Curriculum curriculum = new Curriculum();
                curriculum.setUnit(unit);
                curriculum.setProgramme(courseRepository.findById(courseId).orElseThrow());
                curriculum.setYearOfStudy(1);
                curriculum.setSemesterNumber(1);
                curriculumRepository.save(curriculum);
            }
            redirectAttributes.addFlashAttribute("successMessage", "Unit created successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/units";
    }

    @PostMapping("/{id}/link-programme")
    public String linkProgramme(@PathVariable Long id,
                                 @RequestParam Long courseId,
                                 @RequestParam Integer yearOfStudy,
                                 @RequestParam Integer semesterNumber,
                                 RedirectAttributes redirectAttributes) {
        try {
            Unit unit = unitRepository.findById(id).orElseThrow();
            Curriculum curriculum = new Curriculum();
            curriculum.setUnit(unit);
            curriculum.setProgramme(courseRepository.findById(courseId).orElseThrow());
            curriculum.setYearOfStudy(yearOfStudy);
            curriculum.setSemesterNumber(semesterNumber);
            curriculumRepository.save(curriculum);
            redirectAttributes.addFlashAttribute("successMessage", "Unit linked to programme successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/units";
    }



    @PostMapping("/{id}/delete")
    public String deleteUnit(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            unitService.deleteUnit(id);
            redirectAttributes.addFlashAttribute("successMessage", "Unit deleted.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/units";
    }
}

