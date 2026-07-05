package com.unisubmit.controller.admin;

import com.unisubmit.domain.Role;
import com.unisubmit.service.AcademicHierarchyService;
import com.unisubmit.service.CourseService;
import com.unisubmit.service.UnitService;
import com.unisubmit.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.unisubmit.repository.CurriculumRepository;
import com.unisubmit.repository.SubmissionRepository;
import com.unisubmit.domain.SubmissionStatus;

@Controller
@RequestMapping("/admin")
public class AdminDashboardController {

    private final UserService userService;
    private final UnitService unitService;
    private final CourseService courseService;
    private final AcademicHierarchyService academicHierarchyService;
    private final CurriculumRepository curriculumRepository;
    private final SubmissionRepository submissionRepository;

    public AdminDashboardController(UserService userService,
                                    UnitService unitService,
                                    CourseService courseService,
                                    AcademicHierarchyService academicHierarchyService,
                                    CurriculumRepository curriculumRepository,
                                    SubmissionRepository submissionRepository) {
        this.userService = userService;
        this.unitService = unitService;
        this.courseService = courseService;
        this.academicHierarchyService = academicHierarchyService;
        this.curriculumRepository = curriculumRepository;
        this.submissionRepository = submissionRepository;
    }

    @GetMapping({"", "/", "/dashboard"})
    public String dashboard(Model model) {
        model.addAttribute("statStudents",    userService.countByRole(Role.STUDENT));
        model.addAttribute("statLecturers",   userService.countByRole(Role.LECTURER));
        model.addAttribute("statCourses",     courseService.findAll().size());
        model.addAttribute("statUnits",       unitService.findAllUnits().size());
        model.addAttribute("statFaculties",   academicHierarchyService.countFaculties());
        model.addAttribute("statDepartments", academicHierarchyService.countDepartments());
        model.addAttribute("statCurricula",   curriculumRepository.count());

        // Submission counts by status
        model.addAttribute("statDraft",       submissionRepository.countByStatus(SubmissionStatus.DRAFT));
        model.addAttribute("statSubmitted",   submissionRepository.countByStatus(SubmissionStatus.SUBMITTED));
        model.addAttribute("statApproved",    submissionRepository.countByStatus(SubmissionStatus.APPROVED));
        model.addAttribute("statRejected",    submissionRepository.countByStatus(SubmissionStatus.REJECTED));
        model.addAttribute("statProposal",    submissionRepository.countByStatus(SubmissionStatus.PROPOSAL));
        model.addAttribute("statUnderReview", submissionRepository.countByStatus(SubmissionStatus.UNDER_REVIEW));
        model.addAttribute("statFinal",       submissionRepository.countByStatus(SubmissionStatus.FINAL));
        model.addAttribute("statArchived",    submissionRepository.countByStatus(SubmissionStatus.ARCHIVED));

        return "admin/dashboard";
    }
}
