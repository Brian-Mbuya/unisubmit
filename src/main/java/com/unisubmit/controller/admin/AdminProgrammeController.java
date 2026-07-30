package com.unisubmit.controller.admin;

import com.unisubmit.domain.Course;
import com.unisubmit.service.AcademicHierarchyService;
import com.unisubmit.service.CourseService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/programmes")
public class AdminProgrammeController {

    private final CourseService courseService;
    private final AcademicHierarchyService academicHierarchyService;

    public AdminProgrammeController(CourseService courseService, AcademicHierarchyService academicHierarchyService) {
        this.courseService = courseService;
        this.academicHierarchyService = academicHierarchyService;
    }

    @GetMapping
    public String listProgrammes(Model model) {
        model.addAttribute("courses", courseService.findAll());
        model.addAttribute("departments", academicHierarchyService.findAllDepartments());
        return "admin/programmes";
    }

    @PostMapping
    public String createProgramme(@RequestParam Long departmentId,
                                  @RequestParam String name,
                                  @RequestParam String code,
                                  RedirectAttributes ra) {
        try {
            courseService.createCourse(departmentId, name, code);
            ra.addFlashAttribute("success", "Programme created successfully.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error creating programme: " + e.getMessage());
        }
        return "redirect:/admin/programmes";
    }

    @PostMapping("/{id}/update")
    public String updateProgramme(@PathVariable Long id,
                                  @RequestParam Long departmentId,
                                  @RequestParam String name,
                                  @RequestParam String code,
                                  RedirectAttributes ra) {
        try {
            courseService.updateCourse(id, departmentId, name, code);
            ra.addFlashAttribute("success", "Programme updated successfully.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error updating programme: " + e.getMessage());
        }
        return "redirect:/admin/programmes";
    }

    @PostMapping("/{id}/delete")
    public String deleteProgramme(@PathVariable Long id, RedirectAttributes ra) {
        try {
            courseService.deleteCourse(id);
            ra.addFlashAttribute("success", "Programme deleted successfully.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error deleting programme: " + e.getMessage());
        }
        return "redirect:/admin/programmes";
    }
}
