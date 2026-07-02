package com.unisubmit.service;

import com.unisubmit.domain.Curriculum;
import com.unisubmit.domain.Department;
import com.unisubmit.domain.Feedback;
import com.unisubmit.domain.LecturerProfile;
import com.unisubmit.domain.ResearchArea;
import com.unisubmit.domain.Role;
import com.unisubmit.domain.Submission;
import com.unisubmit.domain.SubmissionVersion;
import com.unisubmit.domain.Technology;
import com.unisubmit.domain.Unit;
import com.unisubmit.domain.User;
import com.unisubmit.dto.LecturerMatch;
import com.unisubmit.repository.FeedbackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Ranking tests for lecturer matching — pure aggregation over feedback
 * history, no Spring context.
 */
class LecturerRecommendationServiceTest {

    @Mock
    private FeedbackRepository feedbackRepository;

    private LecturerRecommendationService service;

    private Department department;
    private Curriculum curriculum;
    private long idSequence = 1000;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new LecturerRecommendationService(feedbackRepository);

        department = new Department();
        department.setId(1L);
        Unit unit = new Unit();
        unit.setId(10L);
        unit.setDepartment(department);
        curriculum = new Curriculum();
        curriculum.setId(20L);
        curriculum.setUnit(unit);
    }

    @Test
    void noTagsOnCurrentSubmissionMeansNoRecommendations() {
        Submission current = submission(1L, Set.of(), Set.of());
        assertTrue(service.recommendLecturersFor(current).isEmpty());
    }

    @Test
    void researchAreaOverlapOutranksSingleTechnologyOverlap() {
        Submission current = submission(1L, Set.of("Java"), Set.of("Machine Learning"));

        User techLecturer = lecturer(2L, null);
        User areaLecturer = lecturer(3L, null);

        List<Feedback> history = new ArrayList<>();
        history.add(feedback(techLecturer, submission(11L, Set.of("Java"), Set.of())));
        history.add(feedback(areaLecturer, submission(12L, Set.of(), Set.of("Machine Learning"))));
        when(feedbackRepository.findAllWithReviewedSubmissions()).thenReturn(history);

        List<LecturerMatch> matches = service.recommendLecturersFor(current);

        assertEquals(2, matches.size());
        // area weight 1.5 > tech weight 1.0
        assertEquals(3L, matches.get(0).lecturer().getId());
        assertEquals(2L, matches.get(1).lecturer().getId());
        assertTrue(matches.get(0).score() > matches.get(1).score());
    }

    @Test
    void reviewVolumeBreaksTiesBetweenEqualOverlap() {
        Submission current = submission(1L, Set.of("Java"), Set.of());

        User veteran = lecturer(2L, null);
        User novice = lecturer(3L, null);

        List<Feedback> history = new ArrayList<>();
        // Same single shared tag, but the veteran reviewed three distinct projects
        history.add(feedback(veteran, submission(11L, Set.of("Java"), Set.of())));
        history.add(feedback(veteran, submission(12L, Set.of("Java"), Set.of())));
        history.add(feedback(veteran, submission(13L, Set.of("Java"), Set.of())));
        history.add(feedback(novice, submission(14L, Set.of("Java"), Set.of())));
        when(feedbackRepository.findAllWithReviewedSubmissions()).thenReturn(history);

        List<LecturerMatch> matches = service.recommendLecturersFor(current);

        assertEquals(2L, matches.get(0).lecturer().getId());
        assertEquals(3, matches.get(0).reviewedCount());
    }

    @Test
    void sameDepartmentGetsBonusOverEqualOutsider() {
        Submission current = submission(1L, Set.of("Java"), Set.of());

        Department otherDept = new Department();
        otherDept.setId(2L);
        User insider = lecturer(2L, department);
        User outsider = lecturer(3L, otherDept);

        List<Feedback> history = new ArrayList<>();
        history.add(feedback(insider, submission(11L, Set.of("Java"), Set.of())));
        history.add(feedback(outsider, submission(12L, Set.of("Java"), Set.of())));
        when(feedbackRepository.findAllWithReviewedSubmissions()).thenReturn(history);

        List<LecturerMatch> matches = service.recommendLecturersFor(current);

        assertEquals(2L, matches.get(0).lecturer().getId());
        assertTrue(matches.get(0).sameDepartment());
        assertEquals(3L, matches.get(1).lecturer().getId());
    }

    @Test
    void reviewingTheCurrentSubmissionItselfDoesNotCount() {
        Submission current = submission(1L, Set.of("Java"), Set.of());

        User lecturer = lecturer(2L, null);
        when(feedbackRepository.findAllWithReviewedSubmissions())
                .thenReturn(List.of(feedback(lecturer, current)));

        assertTrue(service.recommendLecturersFor(current).isEmpty());
    }

    @Test
    void atMostThreeLecturersAreReturned() {
        Submission current = submission(1L, Set.of("Java"), Set.of());

        List<Feedback> history = new ArrayList<>();
        for (long i = 2; i <= 6; i++) {
            history.add(feedback(lecturer(i, null),
                    submission(10L + i, Set.of("Java"), Set.of())));
        }
        when(feedbackRepository.findAllWithReviewedSubmissions()).thenReturn(history);

        assertEquals(3, service.recommendLecturersFor(current).size());
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private User lecturer(Long id, Department dept) {
        User user = new User();
        user.setId(id);
        user.setName("Lecturer " + id);
        user.setRole(Role.LECTURER);
        if (dept != null) {
            LecturerProfile profile = new LecturerProfile();
            profile.setId(id + 900);
            profile.setDepartment(dept);
            user.setLecturerProfile(profile);
        }
        return user;
    }

    private Submission submission(Long id, Set<String> technologies, Set<String> researchAreas) {
        Submission submission = new Submission();
        submission.setId(id);
        submission.setCurriculum(curriculum);

        Set<Technology> techSet = new LinkedHashSet<>();
        for (String name : technologies) {
            Technology tech = new Technology();
            tech.setId(idSequence++);
            tech.setName(name);
            techSet.add(tech);
        }
        submission.setTechnologies(techSet);

        Set<ResearchArea> areaSet = new LinkedHashSet<>();
        for (String name : researchAreas) {
            ResearchArea area = new ResearchArea();
            area.setId(idSequence++);
            area.setName(name);
            areaSet.add(area);
        }
        submission.setResearchAreas(areaSet);
        return submission;
    }

    private Feedback feedback(User lecturer, Submission reviewed) {
        SubmissionVersion version = new SubmissionVersion();
        version.setId(idSequence++);
        version.setSubmission(reviewed);

        Feedback feedback = new Feedback();
        feedback.setId(idSequence++);
        feedback.setLecturer(lecturer);
        feedback.setSubmissionVersion(version);
        return feedback;
    }
}
