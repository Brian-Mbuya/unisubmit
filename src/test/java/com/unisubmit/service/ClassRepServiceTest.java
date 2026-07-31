package com.unisubmit.service;

import com.unisubmit.domain.Course;
import com.unisubmit.domain.Curriculum;
import com.unisubmit.domain.StudentProfile;
import com.unisubmit.domain.User;
import com.unisubmit.exception.UnauthorizedException;
import com.unisubmit.repository.CurriculumRepository;
import com.unisubmit.repository.EnrollmentRepository;
import com.unisubmit.repository.LecturerProfileRepository;
import com.unisubmit.repository.StudentProfileRepository;
import com.unisubmit.repository.TeachingAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Containment matrix for the class-rep powers. A rep is a student with extra authority,
 * so every method has to answer the same question: is this action inside THEIR cohort?
 * These pin the four ways out of that box — not a rep, incomplete profile, someone
 * else's unit, someone else's classmate — because a hole in any of them lets a student
 * mutate another cohort's enrolments.
 */
class ClassRepServiceTest {

    @Mock private StudentProfileRepository studentProfileRepository;
    @Mock private CurriculumRepository curriculumRepository;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private LecturerProfileRepository lecturerProfileRepository;
    @Mock private TeachingAssignmentRepository teachingAssignmentRepository;
    @Mock private AnnouncementService announcementService;
    @Mock private NotificationService notificationService;

    private ClassRepService service;

    private User repUser;
    private StudentProfile repProfile;
    private Course programme;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ClassRepService(studentProfileRepository, curriculumRepository,
                enrollmentRepository, lecturerProfileRepository, teachingAssignmentRepository,
                announcementService, notificationService);

        programme = new Course();
        programme.setId(10L);

        repUser = new User();
        repUser.setId(1L);

        repProfile = new StudentProfile();
        repProfile.setId(100L);
        repProfile.setUser(repUser);
        repProfile.setClassRep(true);
        repProfile.setProgramme(programme);
        repProfile.setCurrentYear(3);
        repProfile.setCurrentSemester(1);
    }

    private void callerIs(StudentProfile profile) {
        lenient().when(studentProfileRepository.findByUser_Id(1L)).thenReturn(Optional.of(profile));
    }

    // ── requireRep: who is allowed to act at all ────────────────────────────────

    @Test
    void plainStudentCannotUseClassRepPowers() {
        repProfile.setClassRep(false);
        callerIs(repProfile);

        assertThrows(UnauthorizedException.class, () -> service.myClassmates(repUser));
        assertThrows(UnauthorizedException.class,
                () -> service.enrolClassmate(repUser, 200L, 50L));
    }

    @Test
    void accountWithoutAStudentProfileIsRejected() {
        when(studentProfileRepository.findByUser_Id(1L)).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () -> service.myClassUnits(repUser));
    }

    @Test
    void repWithIncompleteProfileCannotAct() {
        // Without programme/year/semester there is no "class" to scope powers to, so
        // permitting this would mean acting on an undefined cohort.
        repProfile.setCurrentSemester(null);
        callerIs(repProfile);

        assertThrows(UnauthorizedException.class, () -> service.myClassUnits(repUser));
    }

    // ── Scoping: reads are confined to the rep's own cohort ─────────────────────

    @Test
    void classUnitsAreScopedToTheRepsOwnProgrammeYearAndSemester() {
        callerIs(repProfile);
        Curriculum unit = new Curriculum();
        when(curriculumRepository.findByProgrammeIdAndYearOfStudyAndSemesterNumber(10L, 3, 1))
                .thenReturn(List.of(unit));

        assertEquals(1, service.myClassUnits(repUser).size());
        verify(curriculumRepository).findByProgrammeIdAndYearOfStudyAndSemesterNumber(10L, 3, 1);
    }

    @Test
    void classmatesAreScopedToTheRepsOwnCohort() {
        callerIs(repProfile);
        when(studentProfileRepository
                .findByProgrammeIdAndCurrentYearAndCurrentSemesterAndUser_DeletedFalse(10L, 3, 1))
                .thenReturn(List.of(repProfile));

        assertEquals(1, service.myClassmates(repUser).size());
        verify(studentProfileRepository)
                .findByProgrammeIdAndCurrentYearAndCurrentSemesterAndUser_DeletedFalse(10L, 3, 1);
    }

    // ── Writes: the unit must belong to the rep's class ─────────────────────────

    @Test
    void repCannotEnrolIntoAUnitOutsideTheirClass() {
        callerIs(repProfile);
        // No curriculum row for (rep's class, that unit) — i.e. another cohort's unit.
        when(curriculumRepository.findByProgrammeIdAndUnitIdAndYearOfStudyAndSemesterNumber(
                anyLong(), anyLong(), any(), any())).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class,
                () -> service.enrolClassmate(repUser, 200L, 999L));
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    void repCannotPostANoticeToAUnitOutsideTheirClass() {
        callerIs(repProfile);
        when(curriculumRepository.findByProgrammeIdAndUnitIdAndYearOfStudyAndSemesterNumber(
                anyLong(), anyLong(), any(), any())).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class,
                () -> service.postClassNotice(repUser, 999L, "Title", "Message"));
        verify(announcementService, never())
                .createAnnouncement(any(), any(), any(), any(), any(), any());
    }

    @Test
    void repCannotAssignALecturerToAUnitOutsideTheirClass() {
        callerIs(repProfile);
        when(curriculumRepository.findByProgrammeIdAndUnitIdAndYearOfStudyAndSemesterNumber(
                anyLong(), anyLong(), any(), any())).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class,
                () -> service.assignLecturer(repUser, 300L, 999L));
        verify(teachingAssignmentRepository, never()).save(any());
    }

    // ── Writes: the student must belong to the rep's class ──────────────────────

    @Test
    void repCannotEnrolAStudentFromAnotherClass() {
        callerIs(repProfile);
        Curriculum ownUnit = new Curriculum();
        ownUnit.setId(500L);
        when(curriculumRepository.findByProgrammeIdAndUnitIdAndYearOfStudyAndSemesterNumber(
                anyLong(), anyLong(), any(), any())).thenReturn(Optional.of(ownUnit));

        // Same programme, but a different year — a neighbouring cohort, not the rep's.
        StudentProfile outsider = new StudentProfile();
        outsider.setId(200L);
        outsider.setProgramme(programme);
        outsider.setCurrentYear(2);
        outsider.setCurrentSemester(1);
        when(studentProfileRepository.findById(200L)).thenReturn(Optional.of(outsider));

        assertThrows(UnauthorizedException.class,
                () -> service.enrolClassmate(repUser, 200L, 50L));
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    void repCannotEnrolAStudentWithNoProgramme() {
        callerIs(repProfile);
        Curriculum ownUnit = new Curriculum();
        ownUnit.setId(500L);
        when(curriculumRepository.findByProgrammeIdAndUnitIdAndYearOfStudyAndSemesterNumber(
                anyLong(), anyLong(), any(), any())).thenReturn(Optional.of(ownUnit));

        StudentProfile unassigned = new StudentProfile();
        unassigned.setId(201L);
        unassigned.setProgramme(null);
        when(studentProfileRepository.findById(201L)).thenReturn(Optional.of(unassigned));

        // A null programme must not NPE its way into being treated as "same class".
        assertThrows(UnauthorizedException.class,
                () -> service.enrolClassmate(repUser, 201L, 50L));
        verify(enrollmentRepository, never()).save(any());
    }
}
