package com.unisubmit.service;

import com.unisubmit.domain.*;
import com.unisubmit.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CurriculumRepository curriculumRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final UnitRepository unitRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final SubmissionRepository submissionRepository;
    private final com.unisubmit.repository.AppNotificationRepository appNotificationRepository;

    public AnnouncementService(AnnouncementRepository announcementRepository,
                               EnrollmentRepository enrollmentRepository,
                               CurriculumRepository curriculumRepository,
                               NotificationService notificationService,
                               UserRepository userRepository,
                               UnitRepository unitRepository,
                               StudentProfileRepository studentProfileRepository,
                               SubmissionRepository submissionRepository,
                               com.unisubmit.repository.AppNotificationRepository appNotificationRepository) {
        this.announcementRepository = announcementRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.curriculumRepository = curriculumRepository;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.unitRepository = unitRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.submissionRepository = submissionRepository;
        this.appNotificationRepository = appNotificationRepository;
    }

    /**
     * Phase 9 — deadline reminders. For every ASSIGNMENT due within the next
     * 3 days, notify each enrolled/programme student once per window (3-day and
     * 1-day). Dedup is by exact message text, so repeated hourly runs never
     * duplicate a reminder.
     */
    @Transactional
    public int sendDeadlineReminders() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        int sent = 0;
        for (Announcement ann : announcementRepository.findAll()) {
            if (ann.getType() != AnnouncementType.ASSIGNMENT || ann.getDeadline() == null
                    || ann.getUnit() == null) {
                continue;
            }
            long hoursLeft = java.time.temporal.ChronoUnit.HOURS.between(now, ann.getDeadline());
            if (hoursLeft < 0 || hoursLeft > 72) {
                continue; // past due or more than 3 days away
            }
            String window = hoursLeft <= 24 ? "1 day" : "3 days";
            String deadlineText = ann.getDeadline().format(
                    java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"));
            String message = "Reminder: '" + ann.getTitle() + "' in [" + ann.getUnit().getUnitName()
                    + "] is due in " + window + " (by " + deadlineText + ").";

            for (User student : studentsForUnit(ann.getUnit().getId())) {
                if (!appNotificationRepository.existsByRecipientAndMessage(student, message)) {
                    notificationService.createNotification(student, NotificationType.DEADLINE, message, null);
                    sent++;
                }
            }
        }
        return sent;
    }

    /** Distinct students of a unit: explicit enrollments + programme mapping. */
    private List<User> studentsForUnit(Long unitId) {
        Set<Long> seen = new HashSet<>();
        List<User> students = new ArrayList<>();
        for (Curriculum curriculum : curriculumRepository.findByUnitId(unitId)) {
            for (Enrollment e : enrollmentRepository.findByCurriculumId(curriculum.getId())) {
                if ("ENROLLED".equalsIgnoreCase(e.getStatus()) && e.getStudent() != null
                        && e.getStudent().getUser() != null && seen.add(e.getStudent().getUser().getId())) {
                    students.add(e.getStudent().getUser());
                }
            }
            if (curriculum.getProgramme() != null) {
                for (StudentProfile sp : studentProfileRepository.findByProgrammeId(curriculum.getProgramme().getId())) {
                    if (sp.getUser() != null && !sp.getUser().isDeleted() && seen.add(sp.getUser().getId())) {
                        students.add(sp.getUser());
                    }
                }
            }
        }
        return students;
    }

    @Transactional
    public Announcement createAnnouncement(User lecturer, Long unitId, String title, String message, AnnouncementType type, java.time.LocalDateTime deadline) {
        List<Curriculum> curricula = curriculumRepository.findByUnitId(unitId);
        if (curricula.isEmpty()) {
            throw new IllegalArgumentException("Unit not found or not mapped in curriculum: " + unitId);
        }

        Unit unit = curricula.get(0).getUnit();

        Announcement announcement = new Announcement();
        announcement.setLecturer(lecturer);
        announcement.setUnit(unit);
        announcement.setTitle(title);
        announcement.setMessage(message);
        announcement.setType(type);
        announcement.setDeadline(deadline);
        Announcement saved = announcementRepository.save(announcement);

        if (type == AnnouncementType.ASSIGNMENT) {
            refreshUnitDeadline(unit, null);
        }

        // Notify enrolled students
        String noticeText;
        if (type == AnnouncementType.ASSIGNMENT) {
            String deadlineText = deadline != null
                    ? " (Due: " + deadline.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")) + ")"
                    : "";
            noticeText = "New assignment in [" + unit.getUnitName() + "]: " + title + deadlineText;
        } else {
            noticeText = "New announcement in [" + unit.getUnitName() + "]: " + title;
        }
        Set<Long> notifiedUserIds = new HashSet<>();
        for (Curriculum curriculum : curricula) {
            // A. Explicit enrollments
            List<Enrollment> enrollments = enrollmentRepository.findByCurriculumId(curriculum.getId());
            for (Enrollment enrollment : enrollments) {
                if ("ENROLLED".equalsIgnoreCase(enrollment.getStatus()) && enrollment.getStudent() != null) {
                    User studentUser = enrollment.getStudent().getUser();
                    if (studentUser != null && notifiedUserIds.add(studentUser.getId())) {
                        notificationService.createNotification(
                                studentUser,
                                NotificationType.SYSTEM_NOTICE,
                                noticeText,
                                null
                        );
                    }
                }
            }

            // B. Academic programme students
            if (curriculum.getProgramme() != null) {
                List<StudentProfile> programmeStudents = studentProfileRepository.findByProgrammeId(curriculum.getProgramme().getId());
                for (StudentProfile studentProfile : programmeStudents) {
                    User studentUser = studentProfile.getUser();
                    if (studentUser != null && !studentUser.isDeleted() && notifiedUserIds.add(studentUser.getId())) {
                        notificationService.createNotification(
                                studentUser,
                                NotificationType.SYSTEM_NOTICE,
                                noticeText,
                                null
                        );
                    }
                }
            }
        }

        return saved;
    }

    public List<Announcement> getAnnouncementsForUnit(Long unitId) {
        return announcementRepository.findByUnitIdOrderByCreatedAtDesc(unitId);
    }

    public List<Announcement> getAnnouncementsForStudent(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return List.of();
        }

        List<Long> unitIds = new ArrayList<>();

        if (user.getStudentProfile() != null) {
            // 1. Units from explicit enrollments
            List<Enrollment> enrollments = enrollmentRepository.findByStudentId(user.getStudentProfile().getId());
            for (Enrollment e : enrollments) {
                if ("ENROLLED".equalsIgnoreCase(e.getStatus()) && e.getCurriculum() != null && e.getCurriculum().getUnit() != null) {
                    unitIds.add(e.getCurriculum().getUnit().getId());
                }
            }

            // 2. Units from academic programme curriculum
            if (user.getStudentProfile().getProgramme() != null) {
                Long progId = user.getStudentProfile().getProgramme().getId();
                List<Curriculum> curricula = curriculumRepository.findByProgrammeId(progId);
                for (Curriculum c : curricula) {
                    if (c.getUnit() != null) {
                        unitIds.add(c.getUnit().getId());
                    }
                }
            }
        }

        // 3. Units the student has actually submitted work to — covers students
        //    without an enrollment row or programme mapping (previously these
        //    students saw an empty announcements page even though they were
        //    being notified about the unit's assignments).
        for (Submission s : submissionRepository.findByStudent(user)) {
            if (s.getCurriculum() != null && s.getCurriculum().getUnit() != null) {
                unitIds.add(s.getCurriculum().getUnit().getId());
            }
        }

        // Deduplicate unit IDs
        unitIds = unitIds.stream().distinct().collect(Collectors.toList());

        List<Announcement> allAnnouncements = new ArrayList<>();
        for (Long unitId : unitIds) {
            allAnnouncements.addAll(announcementRepository.findByUnitIdOrderByCreatedAtDesc(unitId));
        }

        // Sort by createdAt desc and deduplicate announcements
        allAnnouncements.sort((a1, a2) -> a2.getCreatedAt().compareTo(a1.getCreatedAt()));
        return allAnnouncements.stream().distinct().collect(Collectors.toList());
    }

    /**
     * The single most recent notice for a student, if one was posted in the last 48h —
     * powers the dashboard pop-up so students see fresh notices on login. Returns an empty
     * list (never null) when there is nothing recent, so templates can guard with isEmpty.
     */
    public List<Announcement> getLatestNoticeForStudent(Long userId) {
        java.time.LocalDateTime cutoff = java.time.LocalDateTime.now().minusHours(48);
        return getAnnouncementsForStudent(userId).stream()
                .filter(a -> a.getCreatedAt() != null && a.getCreatedAt().isAfter(cutoff))
                .limit(1)
                .collect(Collectors.toList());
    }

    public List<Announcement> getAnnouncementsByLecturer(Long lecturerId) {
        return announcementRepository.findByLecturerIdOrderByCreatedAtDesc(lecturerId);
    }

    @Transactional
    public void deleteAnnouncement(Long announcementId, User lecturer) {
        Announcement announcement = announcementRepository.findById(announcementId)
                .orElseThrow(() -> new IllegalArgumentException("Announcement not found: " + announcementId));

        if (!announcement.getLecturer().getId().equals(lecturer.getId()) && lecturer.getRole() != Role.ADMIN) {
            throw new IllegalArgumentException("You are not authorized to delete this announcement.");
        }

        Unit unit = announcement.getType() == AnnouncementType.ASSIGNMENT ? announcement.getUnit() : null;

        announcementRepository.delete(announcement);

        if (unit != null) {
            refreshUnitDeadline(unit, announcementId);
        }
    }

    /**
     * Toggles the late-submission window on an ASSIGNMENT announcement.
     * When open, students may still upload new versions even past the deadline;
     * those versions will be flagged as late on both sides.
     */
    @Transactional
    public boolean toggleLateWindow(Long announcementId, User lecturer) {
        Announcement announcement = announcementRepository.findById(announcementId)
                .orElseThrow(() -> new IllegalArgumentException("Announcement not found: " + announcementId));

        if (!announcement.getLecturer().getId().equals(lecturer.getId()) && lecturer.getRole() != Role.ADMIN) {
            throw new IllegalArgumentException("You are not authorized to modify this announcement.");
        }

        boolean newState = !announcement.isLateWindowOpen();
        announcement.setLateWindowOpen(newState);
        announcementRepository.save(announcement);
        return newState;
    }

    /** Returns true when at least one assignment for the given unit has its late window open. */
    public boolean isLateWindowOpen(Long unitId) {
        return announcementRepository.findByUnitIdOrderByCreatedAtDesc(unitId).stream()
                .anyMatch(a -> a.getType() == AnnouncementType.ASSIGNMENT && a.isLateWindowOpen());
    }

    /**
     * Recomputes {@code unit.submissionDeadline} as the nearest FUTURE deadline
     * among the unit's remaining ASSIGNMENT announcements. Multiple assignments
     * on one unit no longer clobber each other's deadline, and deleting one
     * assignment falls back to the next-nearest instead of wiping the field.
     *
     * @param excludeAnnouncementId announcement being deleted in this
     *                              transaction (still visible to queries), or null
     */
    private void refreshUnitDeadline(Unit unit, Long excludeAnnouncementId) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime nearest = announcementRepository
                .findByUnitIdOrderByCreatedAtDesc(unit.getId()).stream()
                .filter(a -> a.getType() == AnnouncementType.ASSIGNMENT)
                .filter(a -> excludeAnnouncementId == null || !a.getId().equals(excludeAnnouncementId))
                .map(Announcement::getDeadline)
                .filter(d -> d != null && d.isAfter(now))
                .min(java.time.LocalDateTime::compareTo)
                .orElse(null);
        unit.setSubmissionDeadline(nearest);
        unitRepository.save(unit);
    }
}
