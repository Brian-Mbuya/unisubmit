package com.unisubmit.config;

import com.unisubmit.domain.*;
import com.unisubmit.repository.*;
import com.unisubmit.service.KnowledgeTagService;
import com.unisubmit.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Phase 8 — opt-in cross-department demo data so the whole platform is testable
 * end-to-end without hand-building a hierarchy: three departments, a reviewing
 * lecturer with teaching assignments, enrolled students, and six analysed
 * submissions with real (unique) attached documents that share broad problem
 * domains across different units — exactly the cross-disciplinary pairings the
 * collaboration engine surfaces.
 * <p>
 * Gated behind {@code unisubmit.demo.seed-collaboration=true} and a marker check
 * so it runs at most once and never touches an existing dataset. Runs before
 * {@link RecommendationRefreshRunner} (@Order 20) so the startup refresh then
 * builds the collaboration shortlist over this data.
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
    private final SubmissionVersionRepository versionRepository;
    private final AIInsightRepository aiInsightRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final LecturerProfileRepository lecturerProfileRepository;
    private final TeachingAssignmentRepository teachingAssignmentRepository;
    private final EnrollmentRepository enrollmentRepository;
    
    private final UserRepository userRepository;
    private final FeedbackRepository feedbackRepository;
    private final ProjectGroupRepository groupRepository;
    private final CollaborationRequestRepository collaborationRequestRepository;
    
    private final Path uploadRoot;

    public CollaborationDemoSeeder(UserService userService, KnowledgeTagService tagService,
                                   FacultyRepository facultyRepository,
                                   DepartmentRepository departmentRepository,
                                   CourseRepository courseRepository,
                                   UnitRepository unitRepository,
                                   CurriculumRepository curriculumRepository,
                                   SubmissionRepository submissionRepository,
                                   SubmissionVersionRepository versionRepository,
                                   AIInsightRepository aiInsightRepository,
                                   StudentProfileRepository studentProfileRepository,
                                   LecturerProfileRepository lecturerProfileRepository,
                                   TeachingAssignmentRepository teachingAssignmentRepository,
                                   EnrollmentRepository enrollmentRepository,
                                   UserRepository userRepository,
                                   FeedbackRepository feedbackRepository,
                                   ProjectGroupRepository groupRepository,
                                   CollaborationRequestRepository collaborationRequestRepository,
                                   @Value("${app.storage.upload-dir:uploads}") String uploadDir) {
        this.userService = userService;
        this.tagService = tagService;
        this.facultyRepository = facultyRepository;
        this.departmentRepository = departmentRepository;
        this.courseRepository = courseRepository;
        this.unitRepository = unitRepository;
        this.curriculumRepository = curriculumRepository;
        this.submissionRepository = submissionRepository;
        this.versionRepository = versionRepository;
        this.aiInsightRepository = aiInsightRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.lecturerProfileRepository = lecturerProfileRepository;
        this.teachingAssignmentRepository = teachingAssignmentRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.userRepository = userRepository;
        this.feedbackRepository = feedbackRepository;
        this.groupRepository = groupRepository;
        this.collaborationRequestRepository = collaborationRequestRepository;
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    /** dept 0=Computer Science, 1=Electrical Engineering, 2=Public Health. */
    private record DemoProject(int dept, String admission, String studentName, int year,
                               SubmissionStatus status, String title, String summary,
                               List<String> objectives, List<String> domains,
                               List<String> techs, List<String> areas) {}

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void run(String... args) {
        boolean alreadyHasNewSeeding = facultyRepository.findAll().stream()
                .anyMatch(f -> "DEMO-HS".equals(f.getCode()));
        if (alreadyHasNewSeeding) {
            return; // already seeded with expanded list
        }

        boolean hasOldSeeding = facultyRepository.findAll().stream()
                .anyMatch(f -> "DEMO-AS".equals(f.getCode()));
        if (hasOldSeeding) {
            log.info("Old demo seeding detected. Wiping old demo data for self-healing update...");
            wipeOldDemoData();
        }

        try {
            seed();
            log.info("Collaboration demo data seeded: 4 faculties, 8 departments, 9 programmes, 1 lecturer, 6 students.");
        } catch (Exception ex) {
            log.warn("Collaboration demo seeding skipped due to error: {}", ex.getMessage(), ex);
        }
    }

    private void seed() throws Exception {
        Files.createDirectories(uploadRoot);

        Faculty facultyAS = new Faculty();
        facultyAS.setName("Faculty of Applied Sciences (Demo)");
        facultyAS.setCode("DEMO-AS");
        facultyAS = facultyRepository.save(facultyAS);

        Faculty facultyHS = new Faculty();
        facultyHS.setName("Faculty of Health Sciences (Demo)");
        facultyHS.setCode("DEMO-HS");
        facultyHS = facultyRepository.save(facultyHS);

        Faculty facultyED = new Faculty();
        facultyED.setName("Faculty of Education (Demo)");
        facultyED.setCode("DEMO-ED");
        facultyED = facultyRepository.save(facultyED);

        Faculty facultyBE = new Faculty();
        facultyBE.setName("Faculty of Business & Economics (Demo)");
        facultyBE.setCode("DEMO-BE");
        facultyBE = facultyRepository.save(facultyBE);

        Department[] depts = {
                // Applied Sciences (0, 1, 2)
                department(facultyAS, "Department of Computer Science", "DEMO-CS"),
                department(facultyAS, "Department of Electrical Engineering", "DEMO-EE"),
                department(facultyAS, "Department of Public Health", "DEMO-PH"),
                // Health Sciences (3, 4)
                department(facultyHS, "Department of Nursing Practice", "DEMO-NUR"),
                department(facultyHS, "Department of Clinical Medicine", "DEMO-MED"),
                // Education (5)
                department(facultyED, "Department of Educational Foundations", "DEMO-EDF"),
                // Business & Economics (6, 7)
                department(facultyBE, "Department of Finance and Accounting", "DEMO-ACC"),
                department(facultyBE, "Department of Business Administration", "DEMO-BAD")
        };

        Course[] programmes = {
                // Applied Sciences (0, 1, 2)
                programme(depts[0], "BSc Computer Science (Demo)", "DEMO-BCS"),
                programme(depts[1], "BSc Electrical Engineering (Demo)", "DEMO-BEE"),
                programme(depts[2], "BSc Public Health (Demo)", "DEMO-BPH"),
                // Health Sciences (3, 4)
                programme(depts[3], "BSc Nursing (Demo)", "DEMO-BNUR"),
                programme(depts[4], "Bachelor of Medicine & Surgery (Demo)", "DEMO-MBCHB"),
                // Education (5, 6)
                programme(depts[5], "Bachelor of Education (Arts) (Demo)", "DEMO-BED-ARTS"),
                programme(depts[5], "Bachelor of Education (Science) (Demo)", "DEMO-BED-SCI"),
                // Business & Economics (7, 8)
                programme(depts[6], "Bachelor of Commerce (Finance) (Demo)", "DEMO-BCOM-FIN"),
                programme(depts[7], "Bachelor of Business Administration (Demo)", "DEMO-BBA")
        };

        Unit[] units = {
                // Applied Sciences (0, 1, 2)
                unit(depts[0], "DCS410", "Applied Machine Learning"),
                unit(depts[1], "DEE420", "Embedded Systems Design"),
                unit(depts[2], "DPH430", "Population Health Analytics"),
                // Health Sciences (3, 4)
                unit(depts[3], "DNUR410", "Advanced Clinical Nursing Practice"),
                unit(depts[4], "DMED420", "Principles of Internal Medicine"),
                // Education (5, 6)
                unit(depts[5], "DED410", "Philosophy of Education"),
                unit(depts[5], "DED420", "Educational Technology Methods"),
                // Business & Economics (7, 8)
                unit(depts[6], "DBC410", "Corporate Financial Management"),
                unit(depts[7], "DBC420", "Strategic Management & Policy")
        };

        Curriculum[] curricula = {
                curriculum(programmes[0], units[0]),
                curriculum(programmes[1], units[1]),
                curriculum(programmes[2], units[2]),
                curriculum(programmes[3], units[3]),
                curriculum(programmes[4], units[4]),
                curriculum(programmes[5], units[5]),
                curriculum(programmes[6], units[6]),
                curriculum(programmes[7], units[7]),
                curriculum(programmes[8], units[8])
        };

        // A reviewing lecturer assigned to all three demo units, so the review
        // queue and blind-review flow are testable against the demo submissions.
        User lecturer = userService.createUser("demo.lecturer", "password123",
                "Prof. Ada Demo", Role.LECTURER, null, "D-L-1");
        LecturerProfile lecturerProfile = lecturerProfileRepository.findByStaffNumber("D-L-1").orElse(null);
        if (lecturerProfile != null) {
            lecturerProfile.setDepartment(depts[0]);
            lecturerProfile.setAcademicRank("Professor");
            lecturerProfileRepository.save(lecturerProfile);
            for (Curriculum c : curricula) {
                TeachingAssignment ta = new TeachingAssignment();
                ta.setLecturer(lecturerProfile);
                ta.setCurriculum(c);
                ta.setRole("PRIMARY");
                ta.setStatus("ACTIVE");
                teachingAssignmentRepository.save(ta);
            }
        }

        List<DemoProject> projects = List.of(
                // ── Transportation cluster (CS + EE) ──
                new DemoProject(0, "D-CS-1", "Amara Okoye", 4, SubmissionStatus.APPROVED,
                        "Deep-Learning Traffic Congestion Forecasting",
                        "An LSTM model predicting urban traffic congestion from historical flow data. "
                                + "Strong on prediction accuracy but lacks real-world sensor data for validation.",
                        List.of("Build an LSTM congestion predictor", "Benchmark against baseline timers",
                                "Validate on a real road network"),
                        List.of("transportation", "urban planning"),
                        List.of("TensorFlow", "Python"),
                        List.of("Machine Learning")),
                new DemoProject(1, "D-EE-1", "Brian Mwangi", 3, SubmissionStatus.SUBMITTED,
                        "IoT Smart Traffic-Light Controller",
                        "A deployed network of street-level IoT sensors and an adaptive traffic-light "
                                + "controller. Collects six months of real vehicle-count time series but has no "
                                + "predictive analytics layer.",
                        List.of("Deploy street-level IoT sensors", "Adaptively time traffic lights",
                                "Publish a live vehicle-count feed"),
                        List.of("transportation", "urban planning"),
                        List.of("EEG Biosensors", "Kubernetes"),
                        List.of("Renewable Energy")),
                // ── Healthcare cluster (CS + PH) ──
                new DemoProject(0, "D-CS-2", "Chloe Njeri", 4, SubmissionStatus.APPROVED,
                        "CNN Medical Image Diagnosis",
                        "A convolutional model classifying diagnostic images. Method is mature but the "
                                + "team needs a labelled clinical dataset and domain validation.",
                        List.of("Train a CNN image classifier", "Reach clinical-grade accuracy",
                                "Obtain a labelled validation dataset"),
                        List.of("healthcare"),
                        List.of("TensorFlow", "Python"),
                        List.of("Machine Learning", "Epidemiology")),
                new DemoProject(2, "D-PH-1", "Daniel Kamau", 3, SubmissionStatus.SUBMITTED,
                        "Epidemic Spread Modelling for County Clinics",
                        "A compartmental model of disease spread using county clinic records. Rich labelled "
                                + "health data, but the statistical model is simple and could be improved with ML.",
                        List.of("Model disease spread across clinics", "Curate a labelled health dataset",
                                "Forecast outbreak hotspots"),
                        List.of("healthcare"),
                        List.of("SPSS Software", "MATLAB Toolkit"),
                        List.of("Epidemiology", "Microbiology")),
                // ── Energy cluster (EE + CS) ──
                new DemoProject(1, "D-EE-2", "Esther Wanjiru", 4, SubmissionStatus.FINAL,
                        "Solar Micro-Grid Load Optimisation",
                        "An optimisation scheme for a deployed campus solar micro-grid. Has live consumption "
                                + "telemetry but no forecasting of future demand.",
                        List.of("Optimise solar micro-grid load", "Stream live consumption telemetry",
                                "Reduce peak-demand cost"),
                        List.of("energy", "environment"),
                        List.of("MATLAB Toolkit", "Docker"),
                        List.of("Renewable Energy", "Fluid Dynamics")),
                new DemoProject(0, "D-CS-3", "Farhan Ali", 2, SubmissionStatus.SUBMITTED,
                        "Energy Consumption Forecasting with Gradient Boosting",
                        "A gradient-boosting forecaster for building energy demand. Good model, but only "
                                + "tested on synthetic data — needs real building telemetry.",
                        List.of("Forecast building energy demand", "Compare boosting vs neural models",
                                "Validate on real telemetry"),
                        List.of("energy", "environment"),
                        List.of("Python", "React"),
                        List.of("Machine Learning")));

        for (DemoProject p : projects) {
            User student = userService.createUser(
                    p.admission().toLowerCase() + "@demo.unisubmit", "password123", p.studentName(),
                    Role.STUDENT, p.admission(), null,
                    depts[p.dept()].getId(), programmes[p.dept()].getId(), p.year(), 1);

            // Enrol the student in their curriculum so announcements/review reach them.
            StudentProfile profile = student.getStudentProfile();
            if (profile != null) {
                Enrollment enrollment = new Enrollment();
                enrollment.setStudent(profile);
                enrollment.setCurriculum(curricula[p.dept()]);
                enrollment.setStatus("ENROLLED");
                enrollmentRepository.save(enrollment);
            }

            createSubmission(student, curricula[p.dept()], p);
        }
    }

    private void createSubmission(User student, Curriculum curriculum, DemoProject p) throws Exception {
        Submission submission = new Submission();
        submission.setTitle(p.title());
        submission.setStudent(student);
        submission.setCurriculum(curriculum);
        submission.setStatus(p.status());
        submission.setTechnologies(mapTechnologies(p.techs()));
        submission.setResearchAreas(mapResearchAreas(p.areas()));
        submission = submissionRepository.save(submission);

        // Attach a real, UNIQUE document so file preview/download/review work and
        // identical-document detection does NOT false-flag the demo set.
        attachDocument(submission, student, p);

        AIInsight insight = new AIInsight();
        insight.setSubmission(submission);
        insight.setStatus(AIInsightStatus.COMPLETED);
        insight.setSummary(p.summary());
        insight.setKeywords(new LinkedHashSet<>(p.domains()));
        insight.setObjectives(new LinkedHashSet<>(p.objectives()));
        insight.setProblemDomains(new LinkedHashSet<>(p.domains()));
        insight.setProblemStatement(p.summary());
        aiInsightRepository.save(insight);

        submission.setAiInsight(insight);
        submissionRepository.save(submission);
    }

    private void attachDocument(Submission submission, User student, DemoProject p) throws Exception {
        String body = "PROJECT PROPOSAL\n\nTitle: " + p.title() + "\n\n"
                + "Abstract\n" + p.summary() + "\n\n"
                + "Objectives\n- " + String.join("\n- ", p.objectives()) + "\n\n"
                + "Application domains: " + String.join(", ", p.domains()) + "\n"
                + "Technologies: " + String.join(", ", p.techs()) + "\n";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

        String slug = p.admission().toLowerCase() + "-proposal.txt";
        String storedName = UUID.randomUUID() + "_" + slug;
        Files.write(uploadRoot.resolve(storedName), bytes);

        SubmissionVersion version = new SubmissionVersion();
        version.setSubmission(submission);
        version.setFilePath(storedName);
        version.setOriginalFileName(slug);
        version.setFileType("text/plain");
        version.setFileSize((long) bytes.length);
        version.setVersionNumber(1);
        version.setUploadedBy(student);
        version.setContentHash(sha256(bytes));
        versionRepository.save(version);
        submission.getVersions().add(version);
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

    private static String sha256(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(bytes);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @org.springframework.transaction.annotation.Transactional
    private void wipeOldDemoData() {
        try {
            // 1. Delete collaboration requests
            collaborationRequestRepository.deleteAll();

            // 2. Delete feedbacks
            feedbackRepository.deleteAll();

            // 3. Nullify circular and referencing keys on all submissions
            submissionRepository.findAll().forEach(s -> {
                s.setProjectGroup(null);
                s.setAiInsight(null);
                submissionRepository.save(s);
            });

            // 4. Delete project groups
            groupRepository.deleteAll();

            // 5. Delete submissions, versions, and AI insights
            submissionRepository.findAll().stream()
                .filter(s -> s.getStudent().getUsername().endsWith("@demo.unisubmit") || "student".equals(s.getStudent().getUsername()))
                .forEach(s -> {
                    versionRepository.deleteAll(s.getVersions());
                    submissionRepository.delete(s);
                });

            aiInsightRepository.deleteAll();

            // 6. Nullify references on ALL student/lecturer profiles to allow Course/Department deletion
            studentProfileRepository.findAll().forEach(sp -> {
                sp.setProgramme(null);
                studentProfileRepository.save(sp);
            });
            lecturerProfileRepository.findAll().forEach(lp -> {
                lp.setDepartment(null);
                lecturerProfileRepository.save(lp);
            });

            // 7. Delete teaching assignments, enrollments, and curricula
            teachingAssignmentRepository.deleteAll();
            enrollmentRepository.deleteAll();
            curriculumRepository.deleteAll();

            // 8. Delete units, courses, departments, faculties
            unitRepository.deleteAll();
            courseRepository.deleteAll();
            departmentRepository.deleteAll();
            facultyRepository.deleteAll();
            
            // 9. Delete demo users and profiles
            userRepository.findAll().stream()
                .filter(u -> u.getUsername().endsWith("@demo.unisubmit") || "demo.lecturer".equals(u.getUsername()))
                .forEach(u -> {
                    if (u.getStudentProfile() != null) studentProfileRepository.delete(u.getStudentProfile());
                    if (u.getLecturerProfile() != null) lecturerProfileRepository.delete(u.getLecturerProfile());
                    userRepository.delete(u);
                });
        } catch (Exception ex) {
            log.warn("Wiping old demo data failed or was partially completed: {}", ex.getMessage(), ex);
        }
    }
}
