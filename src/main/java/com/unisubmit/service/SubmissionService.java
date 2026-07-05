package com.unisubmit.service;

import com.unisubmit.domain.*;
import com.unisubmit.exception.SubmissionNotFoundException;
import com.unisubmit.exception.UnauthorizedException;
import com.unisubmit.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final SubmissionVersionRepository versionRepository;
    private final FeedbackRepository feedbackRepository;
    private final UnitRepository unitRepository;
    private final UserRepository userRepository;
    private final CurriculumRepository curriculumRepository;
    private final TeachingAssignmentRepository teachingAssignmentRepository;
    private final FileStorageService fileStorageService;
    private final AIInsightService aiInsightService;
    private final NotificationService notificationService;
    private final ProjectGroupRepository groupRepository;
    private final AuditService auditService;

    public SubmissionService(SubmissionRepository submissionRepository,
                             SubmissionVersionRepository versionRepository,
                             FeedbackRepository feedbackRepository,
                             UnitRepository unitRepository,
                             UserRepository userRepository,
                             CurriculumRepository curriculumRepository,
                             TeachingAssignmentRepository teachingAssignmentRepository,
                             FileStorageService fileStorageService,
                             AIInsightService aiInsightService,
                             NotificationService notificationService,
                             ProjectGroupRepository groupRepository,
                             AuditService auditService) {
        this.submissionRepository = submissionRepository;
        this.versionRepository = versionRepository;
        this.feedbackRepository = feedbackRepository;
        this.unitRepository = unitRepository;
        this.userRepository = userRepository;
        this.curriculumRepository = curriculumRepository;
        this.teachingAssignmentRepository = teachingAssignmentRepository;
        this.fileStorageService = fileStorageService;
        this.aiInsightService = aiInsightService;
        this.notificationService = notificationService;
        this.groupRepository = groupRepository;
        this.auditService = auditService;
    }

    @Transactional
    public Submission createSubmission(User student, Long unitId, String title, MultipartFile file, Long groupId) {
        Unit unit = unitRepository.findById(unitId)
                .orElseThrow(() -> new SubmissionNotFoundException("Unit not found: " + unitId));

        if (unit.getSubmissionDeadline() != null && java.time.LocalDateTime.now().isAfter(unit.getSubmissionDeadline())) {
            throw new IllegalArgumentException("The submission deadline for this unit has passed. Submissions are closed.");
        }

        // Match curriculum against unit and student's programme
        Curriculum curriculum = null;
        if (student.getStudentProfile() != null && student.getStudentProfile().getProgramme() != null) {
            Long progId = student.getStudentProfile().getProgramme().getId();
            List<Curriculum> curricula = curriculumRepository.findByUnitId(unitId);
            curriculum = curricula.stream()
                    .filter(c -> c.getProgramme() != null && c.getProgramme().getId().equals(progId))
                    .findFirst()
                    .orElse(null);
        }

        // Fallback: use the first curriculum for this unit, or the first one at all
        if (curriculum == null) {
            List<Curriculum> curricula = curriculumRepository.findByUnitId(unitId);
            if (!curricula.isEmpty()) {
                curriculum = curricula.get(0);
            }
        }

        // If still null, do NOT auto-create a phantom curriculum — surface a clear error
        if (curriculum == null) {
            throw new SubmissionNotFoundException(
                    "This unit has not been assigned to any programme yet. " +
                    "Please contact the admin to set up the curriculum.");
        }

        Submission submission = new Submission();
        submission.setTitle(title);
        submission.setStudent(student);
        submission.setCurriculum(curriculum);
        submission.setStatus(SubmissionStatus.SUBMITTED);

        if (groupId != null) {
            ProjectGroup group = groupRepository.findById(groupId)
                    .orElseThrow(() -> new SubmissionNotFoundException("Group not found: " + groupId));
            submission.setProjectGroup(group);
        }

        submission = submissionRepository.save(submission);
        auditService.record(submission, AuditAction.SUBMISSION_CREATED,
                "Project created", student);
        appendNewVersion(submission, file, student);

        // Trigger AI analysis asynchronously (non-blocking)
        final Long submissionId = submission.getId();
        aiInsightService.initiateAnalysis(submission);

        return submission;
    }

    @Transactional
    public Submission addNewVersion(User student, Long submissionId, MultipartFile file) {
        return addNewVersion(student, submissionId, file, null);
    }

    @Transactional
    public Submission addNewVersion(User student, Long submissionId, MultipartFile file, String changesSummary) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new SubmissionNotFoundException(submissionId));

        Unit unit = submission.getCurriculum().getUnit();
        if (unit != null && unit.getSubmissionDeadline() != null && java.time.LocalDateTime.now().isAfter(unit.getSubmissionDeadline())) {
            throw new IllegalArgumentException("The submission deadline for this unit has passed. Submissions are closed.");
        }

        boolean isOwner = submission.getStudent().getId().equals(student.getId());
        boolean isGroupMember = submission.getProjectGroup() != null &&
                submission.getProjectGroup().getMembers().stream().anyMatch(m -> m.getId().equals(student.getId()));

        if (!isOwner && !isGroupMember) {
            throw new UnauthorizedException("You are not authorised to upload to this submission.");
        }

        if (submission.getStatus() == SubmissionStatus.APPROVED) {
            throw new IllegalStateException("Cannot add a version to an already approved submission.");
        }

        submission.setStatus(SubmissionStatus.SUBMITTED);
        submissionRepository.save(submission);
        appendNewVersion(submission, file, student, changesSummary);
        aiInsightService.initiateAnalysis(submission);

        return submission;
    }

    private void appendNewVersion(Submission submission, MultipartFile file, User uploadedBy) {
        appendNewVersion(submission, file, uploadedBy, null);
    }

    private void appendNewVersion(Submission submission, MultipartFile file, User uploadedBy, String changesSummary) {
        String storedFileName = fileStorageService.storeFile(file);
        int nextVersionNum = submission.getVersions().size() + 1;

        SubmissionVersion version = new SubmissionVersion();
        version.setSubmission(submission);
        version.setFilePath(storedFileName);
        version.setOriginalFileName(file.getOriginalFilename());
        version.setFileType(file.getContentType());
        version.setFileSize(file.getSize());
        version.setVersionNumber(nextVersionNum);
        version.setUploadedBy(uploadedBy);
        version.setContentHash(sha256Hex(file));
        if (changesSummary != null && !changesSummary.isBlank()) {
            version.setChangesSummary(changesSummary.trim().length() > 500
                    ? changesSummary.trim().substring(0, 500) : changesSummary.trim());
        }

        submission.getVersions().add(version);
        versionRepository.save(version);

        auditService.record(submission, AuditAction.VERSION_UPLOADED,
                "Version " + version.getVersionNumber() + " uploaded", uploadedBy);
    }

    /** SHA-256 fingerprint of an upload; null (not a failure) when unreadable. */
    private String sha256Hex(MultipartFile file) {
        try (java.io.InputStream in = file.getInputStream()) {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest.digest()) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            return null;
        }
    }

    public List<Submission> getSubmissionsForStudent(User student) {
        List<Submission> submissions = new ArrayList<>(submissionRepository.findByStudent(student));

        List<ProjectGroup> groups = groupRepository.findByMembersContaining(student);
        for (ProjectGroup group : groups) {
            List<Submission> groupSubs = submissionRepository.findByProjectGroupId(group.getId());
            for (Submission gs : groupSubs) {
                if (submissions.stream().noneMatch(s -> s.getId().equals(gs.getId()))) {
                    submissions.add(gs);
                }
            }
        }

        submissions.sort((s1, s2) -> s2.getCreatedAt().compareTo(s1.getCreatedAt()));
        populateLecturersForSubmissions(submissions);
        return submissions;
    }

    public List<Submission> getSubmissionsForLecturer(User lecturer) {
        if (lecturer.getLecturerProfile() == null) {
            return List.of();
        }
        List<TeachingAssignment> assignments = teachingAssignmentRepository.findByLecturerId(lecturer.getLecturerProfile().getId());
        List<Curriculum> assignedCurricula = assignments.stream()
                .map(TeachingAssignment::getCurriculum)
                .collect(Collectors.toList());

        if (assignedCurricula.isEmpty()) return List.of();
        List<Submission> submissions = submissionRepository.findByCurriculumIn(assignedCurricula);
        populateLecturersForSubmissions(submissions);
        return submissions;
    }

    public Submission getSubmissionForLecturer(Long submissionId, User lecturer) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new SubmissionNotFoundException(submissionId));

        if (lecturer.getRole() == Role.ADMIN) {
            populateLecturersForSubmissions(List.of(submission));
            return submission;
        }

        boolean isAssigned = false;
        if (lecturer.getLecturerProfile() != null && submission.getCurriculum() != null) {
            List<TeachingAssignment> asgns = teachingAssignmentRepository.findByCurriculumId(submission.getCurriculum().getId());
            isAssigned = asgns.stream().anyMatch(a -> a.getLecturer().getId().equals(lecturer.getLecturerProfile().getId()));
        }

        if (!isAssigned) {
            throw new UnauthorizedException("You are not assigned to review this submission.");
        }

        populateLecturersForSubmissions(List.of(submission));
        return submission;
    }

    public Submission getSubmissionForStudent(Long submissionId, User student) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new SubmissionNotFoundException(submissionId));

        boolean isOwner = submission.getStudent().getId().equals(student.getId());
        boolean isGroupMember = submission.getProjectGroup() != null &&
                submission.getProjectGroup().getMembers().stream().anyMatch(m -> m.getId().equals(student.getId()));

        if (!isOwner && !isGroupMember) {
            throw new UnauthorizedException("You do not have access to this submission.");
        }
        populateLecturersForSubmissions(List.of(submission));
        return submission;
    }

    /**
     * Batch-loads all TeachingAssignments for the given submissions' curriculum IDs
     * in a SINGLE query, then groups them in memory — eliminates N+1.
     */
    private void populateLecturersForSubmissions(List<Submission> submissions) {
        // Collect all distinct curriculum IDs
        List<Long> curriculumIds = submissions.stream()
                .filter(s -> s.getCurriculum() != null && s.getCurriculum().getUnit() != null)
                .map(s -> s.getCurriculum().getId())
                .distinct()
                .collect(Collectors.toList());

        if (curriculumIds.isEmpty()) return;

        // Single query for all assignments
        List<TeachingAssignment> allAssignments = teachingAssignmentRepository.findByCurriculumIdIn(curriculumIds);

        // Group by curriculum ID for fast lookup
        Map<Long, List<TeachingAssignment>> byCurriculumId = allAssignments.stream()
                .collect(Collectors.groupingBy(ta -> ta.getCurriculum().getId()));

        for (Submission sub : submissions) {
            if (sub.getCurriculum() != null && sub.getCurriculum().getUnit() != null) {
                List<TeachingAssignment> asgns = byCurriculumId.getOrDefault(sub.getCurriculum().getId(), List.of());
                List<User> lecturers = asgns.stream()
                        .map(a -> a.getLecturer().getUser())
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toList());
                sub.getCurriculum().getUnit().setLecturers(lecturers);
            }
        }
    }

    @Transactional
    public void addFeedbackAndReview(Long submissionId, User lecturer, String message, SubmissionStatus newStatus, Integer grade) {
        Submission submission = getSubmissionForLecturer(submissionId, lecturer);

        if (submission.getVersions().isEmpty()) {
            throw new IllegalStateException("No versions to review.");
        }

        SubmissionVersion latestVersion = submission.getVersions().get(submission.getVersions().size() - 1);

        if (message != null && !message.trim().isEmpty()) {
            Feedback feedback = new Feedback();
            feedback.setMessage(message);
            feedback.setLecturer(lecturer);
            feedback.setSubmissionVersion(latestVersion);
            if (grade != null && grade >= 0 && grade <= 100) {
                feedback.setGrade(grade);
            }
            feedbackRepository.save(feedback);

            auditService.record(submission, AuditAction.FEEDBACK_ADDED,
                    "Feedback added by " + lecturer.getName()
                            + (feedback.getGrade() != null ? " (score " + feedback.getGrade() + "/100)" : ""),
                    lecturer);

            // Notify submission owner
            notificationService.createNotification(
                    submission.getStudent(),
                    NotificationType.NEW_FEEDBACK,
                    "New feedback on '" + submission.getTitle() + "' from " + lecturer.getName(),
                    submission.getId());

            // Notify all other group members (if group submission)
            notifyGroupMembersExcept(submission, submission.getStudent(),
                    NotificationType.NEW_FEEDBACK,
                    "New feedback on '" + submission.getTitle() + "' from " + lecturer.getName());
        }

        if (newStatus != null) {
            submission.setStatus(newStatus);
            submissionRepository.save(submission);

            auditService.record(submission, AuditAction.STATUS_CHANGED,
                    "Status changed to " + newStatus.name().replace('_', ' '), lecturer);

            notificationService.createNotification(
                    submission.getStudent(),
                    NotificationType.STATUS_CHANGE,
                    "'" + submission.getTitle() + "' status changed to " + newStatus.name().replace('_', ' '),
                    submission.getId());

            notifyGroupMembersExcept(submission, submission.getStudent(),
                    NotificationType.STATUS_CHANGE,
                    "'" + submission.getTitle() + "' status changed to " + newStatus.name().replace('_', ' '));
        }
    }

    /** Convenience overload for callers that don't supply a grade. */
    @Transactional
    public void addFeedbackAndReview(Long submissionId, User lecturer, String message, SubmissionStatus newStatus) {
        addFeedbackAndReview(submissionId, lecturer, message, newStatus, null);
    }

    /**
     * Sends a notification to all group members EXCEPT the excluded user.
     * No-op if the submission has no group.
     */
    private void notifyGroupMembersExcept(Submission submission, User exclude,
                                           NotificationType type, String message) {
        if (submission.getProjectGroup() == null) return;
        for (User member : submission.getProjectGroup().getMembers()) {
            if (!member.getId().equals(exclude.getId())) {
                notificationService.createNotification(member, type, message, submission.getId());
            }
        }
    }

    @Transactional
    public Submission addSupervisor(Long submissionId, Long lecturerId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new SubmissionNotFoundException(submissionId));

        User lecturer = userRepository.findById(lecturerId)
                .orElseThrow(() -> new SubmissionNotFoundException("User not found: " + lecturerId));

        if (lecturer.getRole() != Role.LECTURER) {
            throw new IllegalArgumentException("Only lecturers can be assigned as supervisors.");
        }

        submission.getSupervisors().add(lecturer);
        return submissionRepository.save(submission);
    }

    @Transactional
    public Submission removeSupervisor(Long submissionId, Long lecturerId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new SubmissionNotFoundException(submissionId));
        submission.getSupervisors().removeIf(l -> l.getId().equals(lecturerId));
        return submissionRepository.save(submission);
    }

    public Unit getUnitById(Long id) {
        return unitRepository.findById(id)
                .orElseThrow(() -> new SubmissionNotFoundException("Unit not found: " + id));
    }

    @Transactional
    public void saveUnit(Unit unit) {
        unitRepository.save(unit);
    }


}
