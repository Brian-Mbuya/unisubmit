package com.unisubmit.service;

import com.unisubmit.domain.Announcement;
import com.unisubmit.domain.AnnouncementType;
import com.unisubmit.domain.Curriculum;
import com.unisubmit.domain.Enrollment;
import com.unisubmit.domain.LecturerProfile;
import com.unisubmit.domain.NotificationType;
import com.unisubmit.domain.StudentProfile;
import com.unisubmit.domain.TeachingAssignment;
import com.unisubmit.domain.User;
import com.unisubmit.exception.UnauthorizedException;
import com.unisubmit.repository.CurriculumRepository;
import com.unisubmit.repository.EnrollmentRepository;
import com.unisubmit.repository.LecturerProfileRepository;
import com.unisubmit.repository.StudentProfileRepository;
import com.unisubmit.repository.TeachingAssignmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Everything a class representative is allowed to do — and, more importantly, the wall
 * around it.
 *
 * <h2>The rule this whole class enforces</h2>
 * A rep may only ever act on <b>their own class</b>: the (programme, year, semester)
 * triple taken from their OWN {@link StudentProfile}. The class is never passed in as a
 * parameter, because a parameter can be tampered with — {@link #requireRep} re-reads the
 * profile from the database on every call and derives the scope from it. A rep therefore
 * cannot reach another cohort no matter what ids they post.
 *
 * <h2>What a rep deliberately cannot do</h2>
 * Create accounts, set or reset passwords, appoint another rep, grade anything, or remove
 * a student's submitted work. Reps <i>connect existing people to existing units</i> — the
 * clerical work that otherwise lands on a lecturer. Account creation stays with the admin
 * (and the CSV importer), which is what keeps the privilege from escalating: the worst a
 * hostile rep can do is enrol the wrong existing person in a unit, which an admin or
 * lecturer can see and undo.
 */
@Service
public class ClassRepService {

    private static final Logger log = LoggerFactory.getLogger(ClassRepService.class);

    private final StudentProfileRepository studentProfileRepository;
    private final CurriculumRepository curriculumRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final LecturerProfileRepository lecturerProfileRepository;
    private final TeachingAssignmentRepository teachingAssignmentRepository;
    private final AnnouncementService announcementService;
    private final NotificationService notificationService;

    public ClassRepService(StudentProfileRepository studentProfileRepository,
                           CurriculumRepository curriculumRepository,
                           EnrollmentRepository enrollmentRepository,
                           LecturerProfileRepository lecturerProfileRepository,
                           TeachingAssignmentRepository teachingAssignmentRepository,
                           AnnouncementService announcementService,
                           NotificationService notificationService) {
        this.studentProfileRepository = studentProfileRepository;
        this.curriculumRepository = curriculumRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.lecturerProfileRepository = lecturerProfileRepository;
        this.teachingAssignmentRepository = teachingAssignmentRepository;
        this.announcementService = announcementService;
        this.notificationService = notificationService;
    }

    /**
     * Re-reads the caller's profile and asserts they are still a rep with a complete
     * class (programme + year + semester). Called at the top of every mutating method —
     * the {@code hasAuthority('CLASS_REP')} check on the controller is a fast reject, but
     * that authority was baked into a login token or session and could be stale, so this
     * is the check that actually decides.
     */
    private StudentProfile requireRep(User caller) {
        StudentProfile profile = studentProfileRepository.findByUser_Id(caller.getId())
                .orElseThrow(() -> new UnauthorizedException("No student profile for this account."));
        if (!profile.isClassRep()) {
            throw new UnauthorizedException("You are not a class representative.");
        }
        if (profile.getProgramme() == null || profile.getCurrentYear() == null
                || profile.getCurrentSemester() == null) {
            throw new UnauthorizedException(
                    "Your programme, year and semester must be set before you can act as class rep. "
                            + "Ask an administrator to complete your profile.");
        }
        return profile;
    }

    /** The units that belong to the rep's own class. */
    @Transactional(readOnly = true)
    public List<Curriculum> myClassUnits(User caller) {
        StudentProfile rep = requireRep(caller);
        return curriculumRepository.findByProgrammeIdAndYearOfStudyAndSemesterNumber(
                rep.getProgramme().getId(), rep.getCurrentYear(), rep.getCurrentSemester());
    }

    /** Classmates — same programme, year and semester. */
    @Transactional(readOnly = true)
    public List<StudentProfile> myClassmates(User caller) {
        StudentProfile rep = requireRep(caller);
        return studentProfileRepository
                .findByProgrammeIdAndCurrentYearAndCurrentSemesterAndUser_DeletedFalse(
                        rep.getProgramme().getId(), rep.getCurrentYear(), rep.getCurrentSemester());
    }

    /**
     * Resolves a unit id to the curriculum row for the REP'S class, or rejects it.
     * This is the choke point: every mutating method below funnels its {@code unitId}
     * through here, so an id belonging to another cohort is rejected before any write.
     */
    private Curriculum requireOwnClassUnit(StudentProfile rep, Long unitId) {
        return curriculumRepository
                .findByProgrammeIdAndUnitIdAndYearOfStudyAndSemesterNumber(
                        rep.getProgramme().getId(), unitId,
                        rep.getCurrentYear(), rep.getCurrentSemester())
                .orElseThrow(() -> new UnauthorizedException(
                        "That unit is not part of your class."));
    }

    /**
     * Enrols an existing classmate into one of the rep's class units.
     * <p>
     * Both sides are verified: the unit must belong to the rep's class, and the student
     * must already be in the rep's cohort. A rep cannot pull in a student from elsewhere.
     */
    @Transactional
    public String enrolClassmate(User caller, Long studentProfileId, Long unitId) {
        StudentProfile rep = requireRep(caller);
        Curriculum curriculum = requireOwnClassUnit(rep, unitId);

        StudentProfile student = studentProfileRepository.findById(studentProfileId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found."));

        boolean sameClass = student.getProgramme() != null
                && student.getProgramme().getId().equals(rep.getProgramme().getId())
                && rep.getCurrentYear().equals(student.getCurrentYear())
                && rep.getCurrentSemester().equals(student.getCurrentSemester());
        if (!sameClass) {
            throw new UnauthorizedException("That student is not in your class.");
        }

        boolean already = enrollmentRepository
                .findByStudentIdAndCurriculumId(student.getId(), curriculum.getId()).isPresent();
        if (already) {
            return student.getUser().getName() + " is already enrolled in this unit.";
        }

        Enrollment enrollment = new Enrollment();
        enrollment.setStudent(student);
        enrollment.setCurriculum(curriculum);
        enrollment.setStatus("ENROLLED");
        enrollmentRepository.save(enrollment);

        notificationService.createNotification(student.getUser(), NotificationType.SYSTEM_NOTICE,
                "You were enrolled in " + curriculum.getUnit().getUnitName()
                        + " by your class rep.", null);
        log.info("Class rep {} enrolled student {} into curriculum {}",
                rep.getAdmissionNumber(), student.getAdmissionNumber(), curriculum.getId());
        return student.getUser().getName() + " enrolled in " + curriculum.getUnit().getUnitName() + ".";
    }

    /**
     * Records which existing lecturer teaches one of the rep's class units.
     * <p>
     * This is the "make a lecturer's work easier" piece: the rep knows on day one who
     * actually walked into the lecture hall, whereas the admin often does not. Creating
     * the TeachingAssignment is what puts the class's submissions into that lecturer's
     * review queue, so a rep doing this once unblocks the whole unit.
     */
    @Transactional
    public String assignLecturer(User caller, Long lecturerProfileId, Long unitId) {
        StudentProfile rep = requireRep(caller);
        Curriculum curriculum = requireOwnClassUnit(rep, unitId);

        LecturerProfile lecturer = lecturerProfileRepository.findById(lecturerProfileId)
                .orElseThrow(() -> new IllegalArgumentException("Lecturer not found."));
        if (lecturer.getUser() == null || lecturer.getUser().isDeleted()) {
            throw new IllegalArgumentException("That lecturer account is not active.");
        }

        boolean already = teachingAssignmentRepository
                .findByLecturerIdAndCurriculumId(lecturer.getId(), curriculum.getId())
                .isPresent();
        if (already) {
            return lecturer.getUser().getName() + " is already assigned to this unit.";
        }

        TeachingAssignment assignment = new TeachingAssignment();
        assignment.setLecturer(lecturer);
        assignment.setCurriculum(curriculum);
        // ASSISTANT, not PRIMARY: a rep records who is teaching, but the admin remains
        // the authority on who formally owns the unit.
        assignment.setRole("ASSISTANT");
        teachingAssignmentRepository.save(assignment);

        notificationService.createNotification(lecturer.getUser(), NotificationType.SYSTEM_NOTICE,
                "You were assigned to " + curriculum.getUnit().getUnitName()
                        + " by the class representative.", null);
        log.info("Class rep {} assigned lecturer {} to curriculum {}",
                rep.getAdmissionNumber(), lecturer.getStaffNumber(), curriculum.getId());
        return lecturer.getUser().getName() + " assigned to " + curriculum.getUnit().getUnitName() + ".";
    }

    /**
     * Posts a notice to one of the rep's class units.
     * <p>
     * Reuses {@link AnnouncementService#createAnnouncement} unchanged — the recipient
     * fan-out (enrollments ∪ programme-mapped students) and notification wiring are
     * already correct there, and duplicating them for reps would be two code paths
     * drifting apart. Reps are restricted to {@code ANNOUNCEMENT} with no deadline:
     * setting an assignment deadline is academic authority and stays with the lecturer.
     */
    @Transactional
    public Announcement postClassNotice(User caller, Long unitId, String title, String message) {
        StudentProfile rep = requireRep(caller);
        requireOwnClassUnit(rep, unitId);
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("A notice needs a title.");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("A notice needs a message.");
        }
        log.info("Class rep {} posted a notice to unit {}", rep.getAdmissionNumber(), unitId);
        return announcementService.createAnnouncement(caller, unitId, title.trim(), message.trim(),
                AnnouncementType.ANNOUNCEMENT, null);
    }
}
