package com.unisubmit.config;

import com.unisubmit.domain.*;
import com.unisubmit.repository.*;
import com.unisubmit.service.KnowledgeTagService;
import com.unisubmit.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Phase 8 — opt-in cross-department demo data so collaboration discovery is
 * visible immediately (even with no API key / no SPECTER sidecar).
 * <p>
 * Gated behind {@code unisubmit.demo.seed-collaboration=true} (set in the local
 * profile) AND a marker check, so it runs at most once and never touches an
 * existing dataset. Creates three departments whose projects share broad
 * problem domains (transportation, healthcare, energy) across different units —
 * exactly the cross-disciplinary pairings Stage 1 is built to surface.
 * Runs before {@link RecommendationRefreshRunner} (@Order 20) so the startup
 * refresh then builds the collaboration shortlist over this data.
 */
@Component
@Order(10)
@ConditionalOnProperty(name = "unisubmit.demo.seed-collaboration", havingValue = "true")
public class CollaborationDemoSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CollaborationDemoSeeder.class);
    private static final String MARKER_ADMISSION = "D-CS-1";

    private final UserService userService;
    private final KnowledgeTagService tagService;
    private final FacultyRepository facultyRepository;
    private final DepartmentRepository departmentRepository;
    private final CourseRepository courseRepository;
    private final UnitRepository unitRepository;
    private final CurriculumRepository curriculumRepository;
    private final SubmissionRepository submissionRepository;
    private final AIInsightRepository aiInsightRepository;
    private final StudentProfileRepository studentProfileRepository;

    public CollaborationDemoSeeder(UserService userService, KnowledgeTagService tagService,
                                   FacultyRepository facultyRepository,
                                   DepartmentRepository departmentRepository,
                                   CourseRepository courseRepository,
                                   UnitRepository unitRepository,
                                   CurriculumRepository curriculumRepository,
                                   SubmissionRepository submissionRepository,
                                   AIInsightRepository aiInsightRepository,
                                   StudentProfileRepository studentProfileRepository) {
        this.userService = userService;
        this.tagService = tagService;
        this.facultyRepository = facultyRepository;
        this.departmentRepository = departmentRepository;
        this.courseRepository = courseRepository;
        this.unitRepository = unitRepository;
        this.curriculumRepository = curriculumRepository;
        this.submissionRepository = submissionRepository;
        this.aiInsightRepository = aiInsightRepository;
        this.studentProfileRepository = studentProfileRepository;
    }

    /** dept 0=Computer Science, 1=Electrical Engineering, 2=Public Health. */
    private record DemoProject(int dept, String admission, String studentName, int year,
                               SubmissionStatus status, String title, String summary,
                               List<String> domains, List<String> techs, List<String> areas) {}

    @Override
    public void run(String... args) {
        if (studentProfileRepository.findByAdmissionNumber(MARKER_ADMISSION).isPresent()) {
            return; // already seeded
        }
        try {
            seed();
            log.info("Collaboration demo data seeded (3 departments, cross-disciplinary projects).");
        } catch (Exception ex) {
            log.warn("Collaboration demo seeding skipped due to error: {}", ex.getMessage());
        }
    }

    private void seed() {
        Faculty faculty = new Faculty();
        faculty.setName("Faculty of Applied Sciences (Demo)");
        faculty.setCode("DEMO-AS");
        faculty = facultyRepository.save(faculty);

        Department[] depts = {
                department(faculty, "Department of Computer Science", "DEMO-CS"),
                department(faculty, "Department of Electrical Engineering", "DEMO-EE"),
                department(faculty, "Department of Public Health", "DEMO-PH")
        };
        Course[] programmes = {
                programme(depts[0], "BSc Computer Science (Demo)", "DEMO-BCS"),
                programme(depts[1], "BSc Electrical Engineering (Demo)", "DEMO-BEE"),
                programme(depts[2], "BSc Public Health (Demo)", "DEMO-BPH")
        };
        Curriculum[] curricula = {
                curriculum(programmes[0], unit(depts[0], "DCS410", "Applied Machine Learning")),
                curriculum(programmes[1], unit(depts[1], "DEE420", "Embedded Systems Design")),
                curriculum(programmes[2], unit(depts[2], "DPH430", "Population Health Analytics"))
        };

        List<DemoProject> projects = List.of(
                // ── Transportation cluster (CS + EE) ──
                new DemoProject(0, "D-CS-1", "Amara Okoye", 4, SubmissionStatus.APPROVED,
                        "Deep-Learning Traffic Congestion Forecasting",
                        "An LSTM model predicting urban traffic congestion from historical flow data. "
                                + "Strong on prediction accuracy but lacks real-world sensor data for validation.",
                        List.of("transportation", "urban planning"),
                        List.of("TensorFlow", "Python"),
                        List.of("Machine Learning")),
                new DemoProject(1, "D-EE-1", "Brian Mwangi", 3, SubmissionStatus.SUBMITTED,
                        "IoT Smart Traffic-Light Controller",
                        "A deployed network of street-level IoT sensors and an adaptive traffic-light "
                                + "controller. Collects six months of real vehicle-count time series but has no "
                                + "predictive analytics layer.",
                        List.of("transportation", "urban planning"),
                        List.of("EEG Biosensors", "Kubernetes"),
                        List.of("Renewable Energy")),
                // ── Healthcare cluster (CS + PH + EE) ──
                new DemoProject(0, "D-CS-2", "Chloe Njeri", 4, SubmissionStatus.APPROVED,
                        "CNN Medical Image Diagnosis",
                        "A convolutional model classifying diagnostic images. Method is mature but the "
                                + "team needs a labelled clinical dataset and domain validation.",
                        List.of("healthcare"),
                        List.of("TensorFlow", "Python"),
                        List.of("Machine Learning", "Epidemiology")),
                new DemoProject(2, "D-PH-1", "Daniel Kamau", 3, SubmissionStatus.SUBMITTED,
                        "Epidemic Spread Modelling for County Clinics",
                        "A compartmental model of disease spread using county clinic records. Rich labelled "
                                + "health data, but the statistical model is simple and could be improved with ML.",
                        List.of("healthcare"),
                        List.of("SPSS Software", "MATLAB Toolkit"),
                        List.of("Epidemiology", "Microbiology")),
                // ── Energy cluster (EE + CS) ──
                new DemoProject(1, "D-EE-2", "Esther Wanjiru", 4, SubmissionStatus.FINAL,
                        "Solar Micro-Grid Load Optimisation",
                        "An optimisation scheme for a deployed campus solar micro-grid. Has live consumption "
                                + "telemetry but no forecasting of future demand.",
                        List.of("energy", "environment"),
                        List.of("MATLAB Toolkit", "Docker"),
                        List.of("Renewable Energy", "Fluid Dynamics")),
                new DemoProject(0, "D-CS-3", "Farhan Ali", 2, SubmissionStatus.SUBMITTED,
                        "Energy Consumption Forecasting with Gradient Boosting",
                        "A gradient-boosting forecaster for building energy demand. Good model, but only "
                                + "tested on synthetic data — needs real building telemetry.",
                        List.of("energy", "environment"),
                        List.of("Python", "React"),
                        List.of("Machine Learning"))
        );

        for (DemoProject p : projects) {
            User student = userService.createUser(
                    p.admission().toLowerCase() + "@demo.unisubmit", "password123", p.studentName(),
                    Role.STUDENT, p.admission(), null,
                    depts[p.dept()].getId(), programmes[p.dept()].getId(), p.year(), 1);
            createSubmission(student, curricula[p.dept()], p);
        }
    }

    private void createSubmission(User student, Curriculum curriculum, DemoProject p) {
        Submission submission = new Submission();
        submission.setTitle(p.title());
        submission.setStudent(student);
        submission.setCurriculum(curriculum);
        submission.setStatus(p.status());
        submission.setTechnologies(mapTechnologies(p.techs()));
        submission.setResearchAreas(mapResearchAreas(p.areas()));
        submission = submissionRepository.save(submission);

        AIInsight insight = new AIInsight();
        insight.setSubmission(submission);
        insight.setStatus(AIInsightStatus.COMPLETED);
        insight.setSummary(p.summary());
        insight.setKeywords(new LinkedHashSet<>(p.domains()));
        insight.setProblemDomains(new LinkedHashSet<>(p.domains()));
        insight.setProblemStatement(p.summary());
        aiInsightRepository.save(insight);

        submission.setAiInsight(insight);
        submissionRepository.save(submission);
    }

    // ── Builders ─────────────────────────────────────────────────────────────

    private Department department(Faculty faculty, String name, String code) {
        Department d = new Department();
        d.setName(name);
        d.setCode(code);
        d.setFaculty(faculty);
        return departmentRepository.save(d);
    }

    private Course programme(Department dept, String name, String code) {
        Course c = new Course();
        c.setName(name);
        c.setCode(code);
        c.setDepartment(dept);
        return courseRepository.save(c);
    }

    private Unit unit(Department dept, String code, String name) {
        Unit u = new Unit();
        u.setUnitCode(code);
        u.setUnitName(name);
        u.setDepartment(dept);
        return unitRepository.save(u);
    }

    private Curriculum curriculum(Course programme, Unit unit) {
        Curriculum c = new Curriculum();
        c.setProgramme(programme);
        c.setUnit(unit);
        c.setYearOfStudy(1);
        c.setSemesterNumber(1);
        return curriculumRepository.save(c);
    }

    private Set<Technology> mapTechnologies(List<String> names) {
        Set<Technology> set = new LinkedHashSet<>();
        names.forEach(n -> set.add(tagService.findOrCreateTechnology(n)));
        return set;
    }

    private Set<ResearchArea> mapResearchAreas(List<String> names) {
        Set<ResearchArea> set = new LinkedHashSet<>();
        names.forEach(n -> set.add(tagService.findOrCreateResearchArea(n)));
        return set;
    }
}
