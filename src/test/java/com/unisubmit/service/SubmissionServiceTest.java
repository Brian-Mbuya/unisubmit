package com.unisubmit.service;

import com.unisubmit.domain.*;
import com.unisubmit.repository.SubmissionRepository;
import com.unisubmit.repository.TeachingAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubmissionServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private TeachingAssignmentRepository teachingAssignmentRepository;

    @InjectMocks
    private SubmissionService submissionService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetSubmissionForLecturer_AssignedLecturer() {
        User lecturer = new User();
        lecturer.setId(1L);
        lecturer.setRole(Role.LECTURER);
        LecturerProfile lp = new LecturerProfile();
        lp.setId(1L);
        lecturer.setLecturerProfile(lp);

        Curriculum curriculum = new Curriculum();
        curriculum.setId(1L);

        TeachingAssignment assignment = new TeachingAssignment();
        assignment.setCurriculum(curriculum);
        assignment.setLecturer(lp);

        Submission submission = new Submission();
        submission.setId(1L);
        submission.setCurriculum(curriculum);

        when(submissionRepository.findById(1L)).thenReturn(Optional.of(submission));
        when(teachingAssignmentRepository.findByCurriculumId(1L)).thenReturn(List.of(assignment));

        Submission result = submissionService.getSubmissionForLecturer(1L, lecturer);

        assertNotNull(result);
        assertEquals(1L, result.getId());
    }

    @Test
    void testGetSubmissionForLecturer_UnassignedLecturerThrowsException() {
        User lecturer = new User();
        lecturer.setId(1L);
        lecturer.setRole(Role.LECTURER);
        LecturerProfile lp = new LecturerProfile();
        lp.setId(1L);
        lecturer.setLecturerProfile(lp);

        User anotherLecturer = new User();
        anotherLecturer.setId(2L);
        anotherLecturer.setRole(Role.LECTURER);
        LecturerProfile lp2 = new LecturerProfile();
        lp2.setId(2L);
        anotherLecturer.setLecturerProfile(lp2);

        Curriculum curriculum = new Curriculum();
        curriculum.setId(1L);

        TeachingAssignment assignment = new TeachingAssignment();
        assignment.setCurriculum(curriculum);
        assignment.setLecturer(lp2);

        Submission submission = new Submission();
        submission.setId(1L);
        submission.setCurriculum(curriculum);

        when(submissionRepository.findById(1L)).thenReturn(Optional.of(submission));
        when(teachingAssignmentRepository.findByCurriculumId(1L)).thenReturn(List.of(assignment));

        Exception exception = assertThrows(RuntimeException.class, () -> {
            submissionService.getSubmissionForLecturer(1L, lecturer);
        });

        assertTrue(exception.getMessage().contains("assigned") || exception.getMessage().contains("authorised"));
    }
}
