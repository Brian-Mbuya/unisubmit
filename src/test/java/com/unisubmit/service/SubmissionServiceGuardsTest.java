package com.unisubmit.service;

import com.unisubmit.domain.AIInsight;
import com.unisubmit.domain.AIInsightStatus;
import com.unisubmit.domain.Course;
import com.unisubmit.domain.Curriculum;
import com.unisubmit.domain.Role;
import com.unisubmit.domain.StudentProfile;
import com.unisubmit.domain.Submission;
import com.unisubmit.domain.SubmissionStatus;
import com.unisubmit.domain.SubmissionVersion;
import com.unisubmit.domain.Unit;
import com.unisubmit.domain.User;
import com.unisubmit.exception.SubmissionNotFoundException;
import com.unisubmit.exception.UnauthorizedException;
import com.unisubmit.repository.CurriculumRepository;
import com.unisubmit.repository.FeedbackRepository;
import com.unisubmit.repository.ProjectGroupRepository;
import com.unisubmit.repository.SubmissionRepository;
import com.unisubmit.repository.SubmissionVersionRepository;
import com.unisubmit.repository.TeachingAssignmentRepository;
import com.unisubmit.repository.UnitRepository;
import com.unisubmit.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The deadline / late-window / curriculum guards on {@link SubmissionService} (2.2 + 2.3).
 * Before the deadline a version is on time; after it, submission is refused unless the
 * lecturer's late window is open (and then the version is flagged late). A student whose
 * programme isn't linked to the unit gets a clear error rather than another queue.
 */
class SubmissionServiceGuardsTest {

    @Mock private SubmissionRepository submissionRepository;
    @Mock private SubmissionVersionRepository versionRepository;
    @Mock private FeedbackRepository feedbackRepository;
    @Mock private UnitRepository unitRepository;
    @Mock private UserRepository userRepository;
    @Mock private CurriculumRepository curriculumRepository;
    @Mock private TeachingAssignmentRepository teachingAssignmentRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private AIInsightService aiInsightService;
    @Mock private NotificationService notificationService;
    @Mock private ProjectGroupRepository groupRepository;
    @Mock private AuditService auditService;
    @Mock private AnnouncementService announcementService;

    private SubmissionService service;
    private User student;
    private MultipartFile file;

    private static final Long UNIT_ID = 10L;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        service = new SubmissionService(submissionRepository, versionRepository, feedbackRepository,
                unitRepository, userRepository, curriculumRepository, teachingAssignmentRepository,
                fileStorageService, aiInsightService, notificationService, groupRepository,
                auditService, announcementService);

        student = user(1L, Role.STUDENT); // no profile → lenient curriculum path by default

        file = org.mockito.Mockito.mock(MultipartFile.class);
        lenient().when(file.getOriginalFilename()).thenReturn("doc.pdf");
        lenient().when(file.getContentType()).thenReturn("application/pdf");
        lenient().when(file.getSize()).thenReturn(1_000L);
        lenient().when(file.getInputStream())
                .thenAnswer(inv -> new ByteArrayInputStream("hello".getBytes()));

        lenient().when(fileStorageService.storeFile(any())).thenReturn("stored.pdf");
        // save returns its argument so createSubmission can read the persisted instance back.
        lenient().when(submissionRepository.save(any(Submission.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ── 2.2 deadline × late-window ───────────────────────────────────────────

    @Test
    void beforeDeadlineCreatesAnOnTimeVersion() {
        stubUnit(LocalDateTime.now().plusDays(1));
        stubLenientCurriculum();

        service.createSubmission(student, UNIT_ID, "Title", file, null);

        assertFalse(capturedVersion().isLate(), "a pre-deadline upload is not late");
    }

    @Test
    void afterDeadlineWithClosedWindowIsRefused() {
        stubUnit(LocalDateTime.now().minusDays(1));
        when(announcementService.isLateWindowOpen(UNIT_ID)).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> service.createSubmission(student, UNIT_ID, "Title", file, null));
    }

    @Test
    void afterDeadlineWithOpenWindowIsAcceptedAndFlaggedLate() {
        stubUnit(LocalDateTime.now().minusDays(1));
        when(announcementService.isLateWindowOpen(UNIT_ID)).thenReturn(true);
        stubLenientCurriculum();

        service.createSubmission(student, UNIT_ID, "Title", file, null);

        assertTrue(capturedVersion().isLate(), "a late-window upload must be flagged late");
    }

    // ── 2.3 strict curriculum ────────────────────────────────────────────────

    @Test
    void studentWithProgrammeButNoMatchingCurriculumGetsClearError() {
        stubUnit(null);

        Course programme = new Course();
        programme.setId(1L);
        StudentProfile profile = new StudentProfile();
        profile.setProgramme(programme);
        student.setStudentProfile(profile);

        // A curriculum exists for the unit, but for a DIFFERENT programme.
        Course otherProgramme = new Course();
        otherProgramme.setId(2L);
        Curriculum foreign = new Curriculum();
        foreign.setId(50L);
        foreign.setProgramme(otherProgramme);
        when(curriculumRepository.findByUnitId(UNIT_ID)).thenReturn(List.of(foreign));

        assertThrows(SubmissionNotFoundException.class,
                () -> service.createSubmission(student, UNIT_ID, "Title", file, null));
    }

    // ── addNewVersion guards ─────────────────────────────────────────────────

    @Test
    void approvedSubmissionRejectsANewVersion() {
        Submission submission = ownedSubmission(SubmissionStatus.APPROVED);
        when(submissionRepository.findById(200L)).thenReturn(Optional.of(submission));

        assertThrows(IllegalStateException.class,
                () -> service.addNewVersion(student, 200L, file));
    }

    @Test
    void nonMemberCannotAddAVersion() {
        Submission submission = ownedSubmission(SubmissionStatus.SUBMITTED);
        User outsider = user(99L, Role.STUDENT);
        when(submissionRepository.findById(200L)).thenReturn(Optional.of(submission));

        assertThrows(UnauthorizedException.class,
                () -> service.addNewVersion(outsider, 200L, file));
    }

    // ── 4.2 version snapshot ─────────────────────────────────────────────────

    @Test
    void newVersionSnapshotsThePriorInsight() {
        Submission submission = ownedSubmission(SubmissionStatus.SUBMITTED);
        AIInsight insight = new AIInsight();
        insight.setStatus(AIInsightStatus.COMPLETED);
        insight.setSummary("v1 summary");
        insight.setKeywords(new java.util.LinkedHashSet<>(List.of("ml", "nlp")));
        submission.setAiInsight(insight);
        when(submissionRepository.findById(200L)).thenReturn(Optional.of(submission));

        service.addNewVersion(student, 200L, file, "changes");

        // The new version row carries the PRIOR (v1) insight, captured before re-analysis.
        ArgumentCaptor<SubmissionVersion> captor = ArgumentCaptor.forClass(SubmissionVersion.class);
        verify(versionRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        SubmissionVersion latest = captor.getValue();
        assertEquals("v1 summary", latest.getInsightSummarySnapshot());
        assertEquals("ml, nlp", latest.getInsightKeywordsSnapshot());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void stubUnit(LocalDateTime deadline) {
        Unit unit = new Unit();
        unit.setId(UNIT_ID);
        unit.setSubmissionDeadline(deadline);
        when(unitRepository.findById(UNIT_ID)).thenReturn(Optional.of(unit));
    }

    private void stubLenientCurriculum() {
        Curriculum curriculum = new Curriculum();
        curriculum.setId(20L);
        when(curriculumRepository.findByUnitId(UNIT_ID)).thenReturn(List.of(curriculum));
    }

    private Submission ownedSubmission(SubmissionStatus status) {
        Submission submission = new Submission();
        submission.setId(200L);
        submission.setStudent(student);
        submission.setStatus(status);
        submission.setCurriculum(new Curriculum()); // unit null → deadline guard skipped
        return submission;
    }

    private SubmissionVersion capturedVersion() {
        ArgumentCaptor<SubmissionVersion> captor = ArgumentCaptor.forClass(SubmissionVersion.class);
        verify(versionRepository).save(captor.capture());
        return captor.getValue();
    }

    private User user(Long id, Role role) {
        User u = new User();
        u.setId(id);
        u.setName("User " + id);
        u.setRole(role);
        return u;
    }
}
