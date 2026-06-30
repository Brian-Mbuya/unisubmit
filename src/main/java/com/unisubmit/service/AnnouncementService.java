package com.unisubmit.service;

import com.unisubmit.domain.*;
import com.unisubmit.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CurriculumRepository curriculumRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final UnitRepository unitRepository;

    public AnnouncementService(AnnouncementRepository announcementRepository,
                               EnrollmentRepository enrollmentRepository,
                               CurriculumRepository curriculumRepository,
                               NotificationService notificationService,
                               UserRepository userRepository,
                               UnitRepository unitRepository) {
        this.announcementRepository = announcementRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.curriculumRepository = curriculumRepository;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.unitRepository = unitRepository;
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
        for (Curriculum curriculum : curricula) {
            List<Enrollment> enrollments = enrollmentRepository.findByCurriculumId(curriculum.getId());
            for (Enrollment enrollment : enrollments) {
                if ("ENROLLED".equalsIgnoreCase(enrollment.getStatus()) && enrollment.getStudent() != null) {
                    User studentUser = enrollment.getStudent().getUser();
                    if (studentUser != null) {
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

        List<Enrollment> enrollments = enrollmentRepository.findByStudentId(user.getStudentProfile().getId());
        List<Long> unitIds = enrollments.stream()
                .filter(e -> "ENROLLED".equalsIgnoreCase(e.getStatus()))
                .map(e -> e.getCurriculum().getUnit().getId())
                .distinct()
                .collect(Collectors.toList());

        List<Announcement> allAnnouncements = new ArrayList<>();
        for (Long unitId : unitIds) {
            allAnnouncements.addAll(announcementRepository.findByUnitIdOrderByCreatedAtDesc(unitId));
        }

        // Sort by createdAt desc
        allAnnouncements.sort((a1, a2) -> a2.getCreatedAt().compareTo(a1.getCreatedAt()));
        return allAnnouncements;
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
