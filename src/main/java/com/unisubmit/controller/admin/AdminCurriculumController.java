package com.unisubmit.controller.admin;

import com.unisubmit.domain.Curriculum;
import com.unisubmit.repository.CourseRepository;
import com.unisubmit.repository.CurriculumRepository;
import com.unisubmit.repository.UnitRepository;
import com.unisubmit.service.CourseService;
import com.unisubmit.service.UnitService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/curricula")
public class AdminCurriculumController {

    private final CurriculumRepository curriculumRepository;
    private final UnitService unitService;
    private final CourseService courseService;
    private final UnitRepository unitRepository;
    private final CourseRepository courseRepository;

    public AdminCurriculumController(CurriculumRepository curriculumRepository,
                                     UnitService unitService,
                                     CourseService courseService,
                                     UnitRepository unitRepository,
                                     CourseRepository courseRepository) {
        this.curriculumRepository = curriculumRepository;
        this.unitService = unitService;
        this.courseService = courseService;
        this.unitRepository = unitRepository;
        this.courseRepository = courseRepository;
    }

    @GetMapping
    public String listCurricula(Model model) {
        model.addAttribute("curricula", curriculumRepository.findAll());
        model.addAttribute("units", unitService.findAllUnits());
        model.addAttribute("courses", courseService.findAll());
        return "admin/curricula";
    }

    @PostMapping
    public String createCurriculum(@RequestParam Long unitId,
                                   @RequestParam Long courseId,
                                   @RequestParam Integer yearOfStudy,
                                   @RequestParam Integer semesterNumber,
                                   RedirectAttributes redirectAttributes) {
        try {
            Curriculum curriculum = new Curriculum();
            curriculum.setUnit(unitRepository.findById(unitId).orElseThrow());
            curriculum.setProgramme(courseRepository.findById(courseId).orElseThrow());
            curriculum.setYearOfStudy(yearOfStudy);
            curriculum.setSemesterNumber(semesterNumber);
            curriculumRepository.save(curriculum);
            redirectAttributes.addFlashAttribute("successMessage", "Curriculum entry created.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/curricula";
    }

    @PostMapping("/{id}/delete")
    public String deleteCurriculum(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            curriculumRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("successMessage", "Curriculum entry deleted.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/curricula";
    }
}
