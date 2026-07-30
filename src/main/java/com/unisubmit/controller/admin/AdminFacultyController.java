package com.unisubmit.controller.admin;

import com.unisubmit.service.AcademicHierarchyService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/faculties")
public class AdminFacultyController {

    private final AcademicHierarchyService academicHierarchyService;

    public AdminFacultyController(AcademicHierarchyService academicHierarchyService) {
        this.academicHierarchyService = academicHierarchyService;
    }

    @GetMapping
    public String listFaculties(Model model) {
        model.addAttribute("faculties", academicHierarchyService.findAllFaculties());
        return "admin/faculties";
    }

    @PostMapping
    public String createFaculty(@RequestParam String name,
                                @RequestParam String code,
                                RedirectAttributes ra) {
        try {
            academicHierarchyService.createFaculty(name.trim(), code.trim().toUpperCase());
            ra.addFlashAttribute("success", "Faculty \"" + name.trim() + "\" created successfully.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Could not create faculty: " + e.getMessage());
        }
        return "redirect:/admin/faculties";
    }

    @PostMapping("/{id}/update")
    public String updateFaculty(@PathVariable Long id,
                                @RequestParam String name,
                                @RequestParam String code,
                                RedirectAttributes ra) {
        try {
            academicHierarchyService.updateFaculty(id, name.trim(), code.trim().toUpperCase());
            ra.addFlashAttribute("success", "Faculty updated successfully.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Could not update faculty: " + e.getMessage());
        }
        return "redirect:/admin/faculties";
    }

    @PostMapping("/{id}/delete")
    public String deleteFaculty(@PathVariable Long id, RedirectAttributes ra) {
        try {
            academicHierarchyService.deleteFaculty(id);
            ra.addFlashAttribute("success", "Faculty deleted.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Could not delete faculty: " + e.getMessage());
        }
        return "redirect:/admin/faculties";
    }
}
