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

    public AnnouncementService(AnnouncementRepository announcementRepository,
                               EnrollmentRepository enrollmentRepository,
                               CurriculumRepository curriculumRepository,
                               NotificationService notificationService,
                               UserRepository userRepository,
                               UnitRepository unitRepository,
                               StudentProfileRepository studentProfileRepository) {
        this.announcementRepository = announcementRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.curriculumRepository = curriculumRepository;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.unitRepository = unitRepository;
        this.studentProfileRepository = studentProfileRepository;
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

        if (type == AnnouncementType.ASSIGNMENT && deadline != null) {
            unit.setSubmissionDeadline(deadline);
            unitRepository.save(unit);
        }

        // Notify enrolled students
        String noticeText = (type == AnnouncementType.ASSIGNMENT) ?
                "New assignment in [" + unit.getUnitName() + "]: " + title + " (Deadline: " + deadline + ")" :
                "New announcement in [" + unit.getUnitName() + "]: " + title;
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
        if (user == null || user.getStudentProfile() == null) {
            return List.of();
        }

        List<Long> unitIds = new ArrayList<>();

        // 1. Get units from explicit enrollments
        List<Enrollment> enrollments = enrollmentRepository.findByStudentId(user.getStudentProfile().getId());
        for (Enrollment e : enrollments) {
            if ("ENROLLED".equalsIgnoreCase(e.getStatus()) && e.getCurriculum() != null && e.getCurriculum().getUnit() != null) {
                unitIds.add(e.getCurriculum().getUnit().getId());
            }
        }

        // 2. Get units from academic programme curriculum
        if (user.getStudentProfile().getProgramme() != null) {
            Long progId = user.getStudentProfile().getProgramme().getId();
            List<Curriculum> curricula = curriculumRepository.findByProgrammeId(progId);
            for (Curriculum c : curricula) {
                if (c.getUnit() != null) {
                    unitIds.add(c.getUnit().getId());
                }
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

        if (announcement.getType() == AnnouncementType.ASSIGNMENT) {
            Unit unit = announcement.getUnit();
            if (unit != null) {
                unit.setSubmissionDeadline(null);
                unitRepository.save(unit);
            }
        }

        announcementRepository.delete(announcement);
    }
}
