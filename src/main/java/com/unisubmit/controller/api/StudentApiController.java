package com.unisubmit.controller.api;

import com.unisubmit.domain.AIInsight;
import com.unisubmit.domain.Submission;
import com.unisubmit.domain.SubmissionVersion;
import com.unisubmit.domain.User;
import com.unisubmit.repository.FeedbackRepository;
import com.unisubmit.service.SubmissionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Student submission API for the Flutter client.
 * <p>
 * <b>Why every method returns a record and none returns an entity.</b> OSIV is on
 * (CODEBASE-MAP §10.4), so a Thymeleaf template can safely walk lazy associations during
 * render. Serializing a {@link Submission} straight to JSON would do the same thing —
 * except Jackson has no idea where to stop, so it would drag in the student, their
 * profile, the curriculum, the programme, the department, the faculty, every version and
 * all six tag collections, and emit a payload measured in hundreds of kilobytes to a
 * phone on campus wifi. The DTOs below are the contract; the entity graph is not.
 * <p>
 * The mapping happens inside {@code @Transactional(readOnly = true)} so the lazy hops
 * resolve against an open session deterministically, rather than relying on OSIV having
 * left one open.
 */
@RestController
@RequestMapping("/api/v1/student")
public class StudentApiController {

    private final SubmissionService submissionService;
    private final FeedbackRepository feedbackRepository;
    private final CurrentUser currentUser;

    public StudentApiController(SubmissionService submissionService,
                                FeedbackRepository feedbackRepository,
                                CurrentUser currentUser) {
        this.submissionService = submissionService;
        this.feedbackRepository = feedbackRepository;
        this.currentUser = currentUser;
    }

    // ── DTOs ────────────────────────────────────────────────────────────────────

    /** List row — deliberately small; the phone renders dozens of these. */
    public record SubmissionSummary(Long id, String title, String status, String unitName,
                                    LocalDateTime updatedAt, int versionCount,
                                    String insightStatus) {}

    public record VersionView(Long id, int number, String originalName, long sizeBytes,
                              LocalDateTime uploadedAt, String changesSummary, boolean late) {}

    public record FeedbackView(Long id, String lecturerName, String message,
                               Integer grade, LocalDateTime createdAt) {}

    public record InsightView(String status, String summary, Set<String> keywords,
                              String problemStatement, Set<String> objectives,
                              String errorMessage) {}

    public record SubmissionDetail(Long id, String title, String status, String unitName,
                                   String helpWanted, LocalDateTime createdAt,
                                   LocalDateTime updatedAt, List<VersionView> versions,
                                   List<FeedbackView> feedback, InsightView insight) {}

    // ── Endpoints ───────────────────────────────────────────────────────────────

    /** Everything the student has submitted, including work owned by their groups. */
    @GetMapping("/submissions")
    @Transactional(readOnly = true)
    public List<SubmissionSummary> listSubmissions() {
        User student = currentUser.require();
        return submissionService.getSubmissionsForStudent(student).stream()
                .sorted(Comparator.comparing(Submission::getUpdatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(StudentApiController::toSummary)
                .toList();
    }

    /**
     * Full detail. Access is delegated to
     * {@code SubmissionService.getSubmissionForStudent}, which already encodes the
     * owner/group/collaborator rules — re-implementing that check here would be a second
     * copy of the authorisation logic, guaranteed to drift.
     */
    @GetMapping("/submissions/{id}")
    @Transactional(readOnly = true)
    public SubmissionDetail getSubmission(@PathVariable Long id) {
        User student = currentUser.require();
        return toDetail(submissionService.getSubmissionForStudent(id, student));
    }

    /**
     * Creates a submission from a multipart upload.
     * <p>
     * Multipart rather than JSON because the file IS the submission — a base64 body would
     * inflate a 25 MB PDF by a third and force the whole thing into memory. Deadline,
     * late-window, curriculum and file-type rules all live in
     * {@code SubmissionService.createSubmission} and apply identically to web and mobile.
     */
    @PostMapping("/submissions")
    @Transactional
    public ResponseEntity<SubmissionDetail> createSubmission(
            @RequestParam String title,
            @RequestParam Long unitId,
            @RequestParam MultipartFile file,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) String helpWanted) {
        User student = currentUser.require();
        Submission created = submissionService.createSubmission(
                student, unitId, title, file, groupId, helpWanted);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDetail(created));
    }

    /** Uploads a replacement version of an existing submission. */
    @PostMapping("/submissions/{id}/versions")
    @Transactional
    public ResponseEntity<SubmissionDetail> addVersion(
            @PathVariable Long id,
            @RequestParam MultipartFile file,
            @RequestParam(required = false) String changesSummary) {
        User student = currentUser.require();
        Submission updated = submissionService.addNewVersion(student, id, file, changesSummary);
        return ResponseEntity.ok(toDetail(updated));
    }

    /**
     * Cheap poll for the async AI pipeline.
     * <p>
     * Separate from the detail endpoint on purpose: the web client polls insight status
     * every 3s (bounded 40×, see app.js), and the phone will do the same. Making it hit
     * the full detail payload each time would multiply that traffic by the whole
     * version and feedback history for no benefit.
     */
    @GetMapping("/submissions/{id}/insight")
    @Transactional(readOnly = true)
    public InsightView insightStatus(@PathVariable Long id) {
        User student = currentUser.require();
        return toInsight(submissionService.getSubmissionForStudent(id, student).getAiInsight());
    }

    // ── Entity → DTO (all called inside a read transaction) ──────────────────────

    private static SubmissionSummary toSummary(Submission s) {
        return new SubmissionSummary(
                s.getId(),
                s.getTitle(),
                s.getStatus() == null ? null : s.getStatus().name(),
                unitNameOf(s),
                s.getUpdatedAt(),
                s.getVersions() == null ? 0 : s.getVersions().size(),
                s.getAiInsight() == null ? "NONE" : s.getAiInsight().getStatus().name());
    }

    private SubmissionDetail toDetail(Submission s) {
        List<VersionView> versions = s.getVersions() == null ? List.of()
                : s.getVersions().stream()
                    .sorted(Comparator.comparing(SubmissionVersion::getId))
                    .map(StudentApiController::toVersion)
                    .toList();

        // Feedback hangs off SubmissionVersion, so it is fetched by query rather than
        // walked from the entity — that also keeps it to one join instead of one query
        // per version.
        List<FeedbackView> feedback = feedbackRepository
                .findBySubmissionIdWithLecturer(s.getId()).stream()
                .map(f -> new FeedbackView(f.getId(),
                        f.getLecturer() == null ? null : f.getLecturer().getName(),
                        f.getMessage(), f.getGrade(), f.getTimestamp()))
                .toList();

        return new SubmissionDetail(
                s.getId(),
                s.getTitle(),
                s.getStatus() == null ? null : s.getStatus().name(),
                unitNameOf(s),
                s.getHelpWanted(),
                s.getCreatedAt(),
                s.getUpdatedAt(),
                versions,
                feedback,
                toInsight(s.getAiInsight()));
    }

    private static VersionView toVersion(SubmissionVersion v) {
        int number = v.getVersionNumber() == null ? 0 : v.getVersionNumber();
        return new VersionView(
                v.getId(),
                number,
                v.getOriginalFileName(),
                v.getFileSize() == null ? 0L : v.getFileSize(),
                v.getUploadedAt(),
                v.getChangesSummary(),
                v.isLate());
    }

    private static InsightView toInsight(AIInsight insight) {
        if (insight == null) {
            return new InsightView("NONE", null, Set.of(), null, Set.of(), null);
        }
        return new InsightView(
                insight.getStatus().name(),
                insight.getSummary(),
                insight.getKeywords() == null ? Set.of() : insight.getKeywords(),
                insight.getProblemStatement(),
                insight.getObjectives() == null ? Set.of() : insight.getObjectives(),
                insight.getErrorMessage());
    }

    private static String unitNameOf(Submission s) {
        return (s.getCurriculum() != null && s.getCurriculum().getUnit() != null)
                ? s.getCurriculum().getUnit().getUnitName() : null;
    }
}
