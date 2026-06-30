package com.unisubmit.controller.admin;

import com.unisubmit.service.AcademicHierarchyService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/departments")
public class AdminDepartmentController {

    private final AcademicHierarchyService academicHierarchyService;

    public AdminDepartmentController(AcademicHierarchyService academicHierarchyService) {
        this.academicHierarchyService = academicHierarchyService;
    }

    @GetMapping
    public String listDepartments(Model model) {
        model.addAttribute("departments", academicHierarchyService.findAllDepartments());
        model.addAttribute("faculties", academicHierarchyService.findAllFaculties());
        return "admin/departments";
    }

    @PostMapping
    public String createDepartment(@RequestParam Long facultyId,
                                   @RequestParam String name,
                                   @RequestParam String code,
                                   RedirectAttributes ra) {
        try {
            academicHierarchyService.createDepartment(facultyId, name.trim(), code.trim().toUpperCase());
            ra.addFlashAttribute("success", "Department \"" + name.trim() + "\" created successfully.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Could not create department: " + e.getMessage());
        }
        return "redirect:/admin/departments";
    }

    @PostMapping("/{id}/update")
    public String updateDepartment(@PathVariable Long id,
                                   @RequestParam Long facultyId,
                                   @RequestParam String name,
                                   @RequestParam String code,
                                   RedirectAttributes ra) {
        try {
            academicHierarchyService.updateDepartment(id, facultyId, name.trim(), code.trim().toUpperCase());
            ra.addFlashAttribute("success", "Department updated successfully.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Could not update department: " + e.getMessage());
        }
        return "redirect:/admin/departments";
    }

    @PostMapping("/{id}/delete")
    public String deleteDepartment(@PathVariable Long id, RedirectAttributes ra) {
        try {
            academicHierarchyService.deleteDepartment(id);
            ra.addFlashAttribute("success", "Department deleted.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Could not delete department: " + e.getMessage());
        }
        return "redirect:/admin/departments";
    }
}
