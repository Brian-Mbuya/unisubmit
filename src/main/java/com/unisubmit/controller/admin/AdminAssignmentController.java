package com.unisubmit.controller.admin;

import com.unisubmit.domain.TeachingAssignment;
import com.unisubmit.repository.CurriculumRepository;
import com.unisubmit.repository.LecturerProfileRepository;
import com.unisubmit.repository.TeachingAssignmentRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/assignments")
public class AdminAssignmentController {

    private final TeachingAssignmentRepository teachingAssignmentRepository;
    private final LecturerProfileRepository lecturerProfileRepository;
    private final CurriculumRepository curriculumRepository;

    public AdminAssignmentController(TeachingAssignmentRepository teachingAssignmentRepository,
                                     LecturerProfileRepository lecturerProfileRepository,
                                     CurriculumRepository curriculumRepository) {
        this.teachingAssignmentRepository = teachingAssignmentRepository;
        this.lecturerProfileRepository = lecturerProfileRepository;
        this.curriculumRepository = curriculumRepository;
    }

    @GetMapping
    public String listAssignments(Model model) {
        model.addAttribute("assignments", teachingAssignmentRepository.findAll());
        model.addAttribute("lecturers", lecturerProfileRepository.findByUser_DeletedFalse());
        model.addAttribute("curricula", curriculumRepository.findAll());
        return "admin/assignments";
    }

    @PostMapping
    public String createAssignment(@RequestParam Long lecturerId,
                                   @RequestParam Long curriculumId,
                                   @RequestParam(required = false) String academicYear,
                                   @RequestParam(required = false) String semester,
                                   RedirectAttributes redirectAttributes) {
        try {
            TeachingAssignment assignment = new TeachingAssignment();
            assignment.setLecturer(lecturerProfileRepository.findById(lecturerId).orElseThrow());
            assignment.setCurriculum(curriculumRepository.findById(curriculumId).orElseThrow());
            assignment.setAcademicYear(academicYear);
            assignment.setSemester(semester);
            teachingAssignmentRepository.save(assignment);
            redirectAttributes.addFlashAttribute("successMessage", "Assignment created.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/assignments";
    }

    @PostMapping("/{id}/edit")
    public String editAssignment(@PathVariable Long id,
                                 @RequestParam Long lecturerId,
                                 @RequestParam Long curriculumId,
                                 @RequestParam(required = false) String academicYear,
                                 @RequestParam(required = false) String semester,
                                 RedirectAttributes redirectAttributes) {
        try {
            TeachingAssignment assignment = teachingAssignmentRepository.findById(id).orElseThrow();
            assignment.setLecturer(lecturerProfileRepository.findById(lecturerId).orElseThrow());
            assignment.setCurriculum(curriculumRepository.findById(curriculumId).orElseThrow());
            assignment.setAcademicYear(academicYear);
            assignment.setSemester(semester);
            teachingAssignmentRepository.save(assignment);
            redirectAttributes.addFlashAttribute("successMessage", "Assignment updated.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/assignments";
    }

    @PostMapping("/{id}/delete")
    public String deleteAssignment(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            teachingAssignmentRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("successMessage", "Assignment deleted.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/assignments";
    }
}
