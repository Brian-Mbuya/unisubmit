package com.unisubmit.config;

import com.unisubmit.domain.*;
import com.unisubmit.repository.*;
import com.unisubmit.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

@Component
@Order(15) // Runs after CollaborationDemoSeeder (Order 10) and before RecommendationRefreshRunner (Order 20)
public class RichTestDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RichTestDataSeeder.class);

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final LecturerProfileRepository lecturerProfileRepository;
    private final DepartmentRepository departmentRepository;
    private final CourseRepository courseRepository;
    private final UnitRepository unitRepository;
    private final CurriculumRepository curriculumRepository;
    private final TeachingAssignmentRepository teachingAssignmentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionVersionRepository versionRepository;
    private final AIInsightRepository aiInsightRepository;
    private final FeedbackRepository feedbackRepository;
    private final AnnouncementRepository announcementRepository;
    private final AppNotificationRepository notificationRepository;
    private final ProjectGroupRepository groupRepository;
    private final CollaborationRequestRepository collaborationRequestRepository;
    
    private final Path uploadRoot;

    public RichTestDataSeeder(UserRepository userRepository,
                              StudentProfileRepository studentProfileRepository,
                              LecturerProfileRepository lecturerProfileRepository,
                              DepartmentRepository departmentRepository,
                              CourseRepository courseRepository,
                              UnitRepository unitRepository,
                              CurriculumRepository curriculumRepository,
                              TeachingAssignmentRepository teachingAssignmentRepository,
                              EnrollmentRepository enrollmentRepository,
                              SubmissionRepository submissionRepository,
                              SubmissionVersionRepository versionRepository,
                              AIInsightRepository aiInsightRepository,
                              FeedbackRepository feedbackRepository,
                              AnnouncementRepository announcementRepository,
                              AppNotificationRepository notificationRepository,
                              ProjectGroupRepository groupRepository,
                              CollaborationRequestRepository collaborationRequestRepository,
                              @org.springframework.beans.factory.annotation.Value("${app.storage.upload-dir:uploads}") String uploadDir) {
        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.lecturerProfileRepository = lecturerProfileRepository;
        this.departmentRepository = departmentRepository;
        this.courseRepository = courseRepository;
        this.unitRepository = unitRepository;
        this.curriculumRepository = curriculumRepository;
        this.teachingAssignmentRepository = teachingAssignmentRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.submissionRepository = submissionRepository;
        this.versionRepository = versionRepository;
        this.aiInsightRepository = aiInsightRepository;
        this.feedbackRepository = feedbackRepository;
        this.announcementRepository = announcementRepository;
        this.notificationRepository = notificationRepository;
        this.groupRepository = groupRepository;
        this.collaborationRequestRepository = collaborationRequestRepository;
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @Override
    public void run(String... args) {
        // Idempotence check: check if our custom announcement exists
        boolean alreadySeeded = announcementRepository.findAll().stream()
                .anyMatch(a -> a.getTitle().equals("[Demo Assignment] Literature Review Draft"));
        if (alreadySeeded) {
            log.info("Rich test data already seeded.");
            return;
        }
        
        try {
            seedRichTestData();
            log.info("Rich test data seeded successfully: default student/lecturer assignments, announcements, groups, collaboration requests, and peer reviews.");
        } catch (Exception ex) {
            log.warn("Rich test data seeding skipped due to error: {}", ex.getMessage(), ex);
        }
    }

    private void seedRichTestData() throws Exception {
        Files.createDirectories(uploadRoot);

        // 1. Fetch default users
        User studentUser = userRepository.findByUsername("student").orElse(null);
        User lecturerUser = userRepository.findByUsername("lecturer").orElse(null);
        if (studentUser == null || lecturerUser == null) {
            log.warn("Default 'student' or 'lecturer' user not found. Seeding aborted.");
            return;
        }

        // 2. Fetch demo Faculty/Department/Course/Unit/Curriculum via stream-filtering
        Department csDept = departmentRepository.findAll().stream()
                .filter(d -> "DEMO-CS".equals(d.getCode()))
                .findFirst().orElse(null);
        Course csCourse = courseRepository.findAll().stream()
                .filter(c -> "DEMO-BCS".equals(c.getCode()))
                .findFirst().orElse(null);
        Unit amlUnit = unitRepository.findAll().stream()
                .filter(u -> "DCS410".equals(u.getUnitCode()))
                .findFirst().orElse(null);
        Curriculum csCurriculum = null;
        if (csCourse != null && amlUnit != null) {
            List<Curriculum> list = curriculumRepository.findByProgrammeId(csCourse.getId());
            for (Curriculum c : list) {
                if (c.getUnit().getId().equals(amlUnit.getId())) {
                    csCurriculum = c;
                    break;
                }
            }
        }

        if (csCurriculum == null) {
            log.warn("Curriculum matching DEMO-BCS and DCS410 not found. Seeding aborted.");
            return;
        }

        // 3. Link default student to Course and enroll in Curriculum
        StudentProfile studentProfile = studentProfileRepository.findByAdmissionNumber("S001").orElse(null);
        if (studentProfile != null && csCourse != null) {
            studentProfile.setProgramme(csCourse);
            studentProfile.setCurrentYear(4);
            studentProfile.setCurrentSemester(1);
            studentProfileRepository.save(studentProfile);
            
            final Long curriculumId = csCurriculum.getId();
            boolean alreadyEnrolled = enrollmentRepository.findAll().stream()
                .anyMatch(e -> e.getStudent().getId().equals(studentProfile.getId()) && e.getCurriculum().getId().equals(curriculumId));
            if (!alreadyEnrolled) {
                Enrollment enrollment = new Enrollment();
                enrollment.setStudent(studentProfile);
                enrollment.setCurriculum(csCurriculum);
                enrollment.setStatus("ENROLLED");
                enrollmentRepository.save(enrollment);
            }
        }

        // 4. Link default lecturer to Department and assign Teaching Assignment
        LecturerProfile lecturerProfile = lecturerProfileRepository.findByStaffNumber("L001").orElse(null);
        if (lecturerProfile != null && csDept != null) {
            lecturerProfile.setDepartment(csDept);
            lecturerProfile.setAcademicRank("Senior Lecturer");
            lecturerProfileRepository.save(lecturerProfile);
            
            final Long curriculumId = csCurriculum.getId();
            boolean alreadyAssigned = teachingAssignmentRepository.findAll().stream()
                .anyMatch(ta -> ta.getLecturer().getId().equals(lecturerProfile.getId()) && ta.getCurriculum().getId().equals(curriculumId));
            if (!alreadyAssigned) {
                TeachingAssignment ta = new TeachingAssignment();
                ta.setLecturer(lecturerProfile);
                ta.setCurriculum(csCurriculum);
                ta.setRole("PRIMARY");
                ta.setStatus("ACTIVE");
                teachingAssignmentRepository.save(ta);
            }
        }

        // 5. Create realistic project submission for student (John Doe)
        Submission sub = new Submission();
        sub.setTitle("Decentralized Academic File Archiving using Blockchain and IPFS");
        sub.setStudent(studentUser);
        sub.setCurriculum(csCurriculum);
        sub.setStatus(SubmissionStatus.SUBMITTED);
        sub = submissionRepository.save(sub);
        
        // Attach document
        String documentBody = "PROJECT PROPOSAL\n\n"
                + "Title: Decentralized Academic File Archiving using Blockchain and IPFS\n\n"
                + "Abstract\n"
                + "This project proposes a tamper-proof decentralized repository for student thesis archives "
                + "leveraging InterPlanetary File System (IPFS) for storage and Ethereum smart contracts for metadata validation. "
                + "By shifting storage from monolithic central servers to peer-to-peer storage pools, we ensure high availability "
                + "and prevent unauthorized modifications of graded project assets.\n\n"
                + "Objectives\n"
                + "- Implement an Ethereum smart contract for file hash registration\n"
                + "- Build a gateway connector to upload files directly to IPFS nodes\n"
                + "- Optimize file accessibility through a localized gateway cache\n\n"
                + "Application domains: blockchain, storage, system architecture\n"
                + "Technologies: Docker, PostgreSQL, Go\n";
        byte[] bytes = documentBody.getBytes(StandardCharsets.UTF_8);
        String slug = "s001-blockchain-proposal.txt";
        String storedName = UUID.randomUUID() + "_" + slug;
        Files.write(uploadRoot.resolve(storedName), bytes);

        SubmissionVersion version = new SubmissionVersion();
        version.setSubmission(sub);
        version.setFilePath(storedName);
        version.setOriginalFileName(slug);
        version.setFileType("text/plain");
        version.setFileSize((long) bytes.length);
        version.setVersionNumber(1);
        version.setUploadedBy(studentUser);
        version.setContentHash(sha256(bytes));
        versionRepository.save(version);
        sub.getVersions().add(version);

        // Create AI Insight
        AIInsight insight = new AIInsight();
        insight.setSubmission(sub);
        insight.setStatus(AIInsightStatus.COMPLETED);
        insight.setSummary("A proposal for a peer-to-peer academic repository combining IPFS distributed hash tables and Ethereum blockchain validation layers.");
        insight.setKeywords(new LinkedHashSet<>(List.of("blockchain", "IPFS", "decentralized storage", "academic archiving")));
        insight.setObjectives(new LinkedHashSet<>(List.of(
                "Deploy an Ethereum metadata validation contract",
                "Integrate client-side IPFS pinning and gateway resolution",
                "Reduce server bandwidth costs via network nodes caching"
        )));
        insight.setProblemDomains(new LinkedHashSet<>(List.of("blockchain", "distributed databases", "academic records")));
        insight.setProblemStatement("Current academic project servers are prone to single points of failure, data decay, and lack immutable proof of grading integrity.");
        aiInsightRepository.save(insight);

        sub.setAiInsight(insight);
        sub = submissionRepository.save(sub);

        // 6. Seed Announcements
        // Announcement 1: General Welcome
        Announcement a1 = new Announcement();
        a1.setTitle("Welcome to Applied Machine Learning");
        a1.setMessage("Dear Students, welcome to the course! We will explore Regression, CNNs, LSTMs, and model deployment strategies this semester. Please make sure to download the syllabus and form your study groups.");
        a1.setLecturer(lecturerUser);
        a1.setUnit(amlUnit);
        a1.setType(AnnouncementType.ANNOUNCEMENT);
        a1.setCreatedAt(LocalDateTime.now().minusDays(5));
        announcementRepository.save(a1);

        // Announcement 2: Proposal draft deadline
        Announcement a2 = new Announcement();
        a2.setTitle("[Demo Assignment] Literature Review Draft");
        a2.setMessage("Please upload your initial project literature review and technology selection draft by the end of next week. Graded reviews are mandatory for progression.");
        a2.setLecturer(lecturerUser);
        a2.setUnit(amlUnit);
        a2.setType(AnnouncementType.ASSIGNMENT);
        a2.setDeadline(LocalDateTime.now().plusDays(7));
        a2.setCreatedAt(LocalDateTime.now().minusDays(2));
        announcementRepository.save(a2);

        // Announcement 3: Project presentation with late submission window
        Announcement a3 = new Announcement();
        a3.setTitle("[Demo Assignment] Final Prototype Presentation");
        a3.setMessage("Submit your finalized project prototype repository and draft report. The default deadline is in 2 days. The late submission window is open for up to 48 hours past deadline with a 10% penalty.");
        a3.setLecturer(lecturerUser);
        a3.setUnit(amlUnit);
        a3.setType(AnnouncementType.ASSIGNMENT);
        a3.setDeadline(LocalDateTime.now().plusDays(2));
        a3.setLateWindowOpen(true);
        a3.setCreatedAt(LocalDateTime.now().minusDays(1));
        announcementRepository.save(a3);

        // 7. Seed Peer Review / Feedback from lecturer to student (John Doe)
        Feedback f1 = new Feedback();
        f1.setLecturer(lecturerUser);
        f1.setSubmissionVersion(version);
        f1.setMessage("Strong proposal, John. Combining IPFS with a blockchain ledger provides excellent tamper-proofing. However, please evaluate the transaction gas costs for high-throughput institutional uploads. Grade awarded: 82.");
        f1.setGrade(82);
        f1.setTimestamp(LocalDateTime.now().minusHours(4));
        feedbackRepository.save(f1);
        
        // Mark submission as APPROVED since it was graded
        sub.setStatus(SubmissionStatus.APPROVED);
        submissionRepository.save(sub);

        // 8. Seed Project Groups
        User studentCS1 = userRepository.findByUsername("d-cs-1@demo.unisubmit").orElse(null);
        User studentEE1 = userRepository.findByUsername("d-ee-1@demo.unisubmit").orElse(null);
        User studentCS2 = userRepository.findByUsername("d-cs-2@demo.unisubmit").orElse(null);
        User studentPH1 = userRepository.findByUsername("d-ph-1@demo.unisubmit").orElse(null);

        if (studentCS1 != null && studentEE1 != null) {
            ProjectGroup g1 = new ProjectGroup();
            g1.setName("Smart Transit Analytics Group");
            g1.setLeader(studentCS1);
            g1.getMembers().add(studentCS1);
            g1.getMembers().add(studentEE1);
            groupRepository.save(g1);
        }

        if (studentCS2 != null && studentPH1 != null) {
            ProjectGroup g2 = new ProjectGroup();
            g2.setName("Clinical AI & Epidemic Forecasting Lab");
            g2.setLeader(studentCS2);
            g2.getMembers().add(studentCS2);
            g2.getMembers().add(studentPH1);
            groupRepository.save(g2);
        }

        // 9. Seed Collaboration Requests
        Submission subCS1 = submissionRepository.findAll().stream()
                .filter(s -> s.getTitle().equals("Deep-Learning Traffic Congestion Forecasting"))
                .findFirst().orElse(null);
        Submission subCS2 = submissionRepository.findAll().stream()
                .filter(s -> s.getTitle().equals("CNN Medical Image Diagnosis"))
                .findFirst().orElse(null);

        // Request 1: PENDING
        if (subCS1 != null && studentEE1 != null && studentCS1 != null) {
            CollaborationRequest req1 = new CollaborationRequest();
            req1.setSubmission(subCS1);
            req1.setSender(studentEE1);
            req1.setRecipient(studentCS1);
            req1.setMessage("Hey Amara! I have a set of street-level IoT traffic sensors deployed that could provide real vehicle time-series data to validate your LSTM forecasting model. Let's merge our systems!");
            req1.setStatus(CollaborationRequestStatus.PENDING);
            req1.setCreatedAt(LocalDateTime.now().minusDays(1));
            collaborationRequestRepository.save(req1);
        }

        // Request 2: ACCEPTED
        if (subCS2 != null && studentPH1 != null && studentCS2 != null) {
            CollaborationRequest req2 = new CollaborationRequest();
            req2.setSubmission(subCS2);
            req2.setSender(studentPH1);
            req2.setRecipient(studentCS2);
            req2.setMessage("Hi Chloe, I have curated a clinical database of outbreak diagnosis files. We could use your CNN classifier to run automated disease staging for these patients.");
            req2.setStatus(CollaborationRequestStatus.ACCEPTED);
            req2.setCreatedAt(LocalDateTime.now().minusDays(3));
            collaborationRequestRepository.save(req2);
        }

        // 10. Seed Notifications
        if (studentUser != null) {
            AppNotification n1 = new AppNotification();
            n1.setRecipient(studentUser);
            n1.setType(NotificationType.SYSTEM_NOTICE);
            n1.setMessage("Welcome to UniSubmit! Explore your new Academic Collaboration dashboard to find cross-disciplinary project recommendations.");
            n1.setRead(false);
            n1.setCreatedAt(LocalDateTime.now().minusHours(12));
            notificationRepository.save(n1);

            AppNotification n2 = new AppNotification();
            n2.setRecipient(studentUser);
            n2.setType(NotificationType.NEW_FEEDBACK);
            n2.setMessage("Feedback posted: Dr. Smith reviewed your project proposal 'Decentralized Academic File Archiving using Blockchain and IPFS'.");
            n2.setRelatedSubmissionId(sub.getId());
            n2.setRead(false);
            n2.setCreatedAt(LocalDateTime.now().minusHours(4));
            notificationRepository.save(n2);
            
            AppNotification n3 = new AppNotification();
            n3.setRecipient(studentUser);
            n3.setType(NotificationType.DEADLINE);
            n3.setMessage("Deadline Alert: '[Demo Assignment] Final Prototype Presentation' is due in 2 days.");
            n3.setRead(false);
            n3.setCreatedAt(LocalDateTime.now().minusHours(2));
            notificationRepository.save(n3);
        }

        if (lecturerUser != null) {
            AppNotification ln1 = new AppNotification();
            ln1.setRecipient(lecturerUser);
            ln1.setType(NotificationType.SYSTEM_NOTICE);
            ln1.setMessage("System Update: High-fidelity book monogram branding successfully integrated across your academic portal.");
            ln1.setRead(false);
            ln1.setCreatedAt(LocalDateTime.now().minusHours(24));
            notificationRepository.save(ln1);

            AppNotification ln2 = new AppNotification();
            ln2.setRecipient(lecturerUser);
            ln2.setType(NotificationType.DEADLINE);
            ln2.setMessage("New submission draft received: John Doe uploaded 'Decentralized Academic File Archiving using Blockchain and IPFS' for review.");
            ln2.setRelatedSubmissionId(sub.getId());
            ln2.setRead(false);
            ln2.setCreatedAt(LocalDateTime.now().minusHours(6));
            notificationRepository.save(ln2);
        }
    }

    private String sha256(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(bytes);
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
