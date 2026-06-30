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

@Controller
@RequestMapping("/admin")
public class AdminDashboardController {

    private final UserService userService;
    private final UnitService unitService;
    private final CourseService courseService;
    private final AcademicHierarchyService academicHierarchyService;
    private final CurriculumRepository curriculumRepository;

    public AdminDashboardController(UserService userService,
                                    UnitService unitService,
                                    CourseService courseService,
                                    AcademicHierarchyService academicHierarchyService,
                                    CurriculumRepository curriculumRepository) {
        this.userService = userService;
        this.unitService = unitService;
        this.courseService = courseService;
        this.academicHierarchyService = academicHierarchyService;
        this.curriculumRepository = curriculumRepository;
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
        return "admin/dashboard";
    }
}
