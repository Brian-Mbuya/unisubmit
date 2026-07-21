package com.unisubmit.service;

import com.unisubmit.config.CollaborationWeights;
import com.unisubmit.domain.AIInsight;
import com.unisubmit.domain.AIInsightStatus;
import com.unisubmit.domain.CollaborationMatch;
import com.unisubmit.domain.Course;
import com.unisubmit.domain.Curriculum;
import com.unisubmit.domain.Department;
import com.unisubmit.domain.MatchReasonType;
import com.unisubmit.domain.Role;
import com.unisubmit.domain.StudentProfile;
import com.unisubmit.domain.Submission;
import com.unisubmit.domain.SubmissionStatus;
import com.unisubmit.domain.Unit;
import com.unisubmit.domain.User;
import com.unisubmit.repository.CollaborationMatchRepository;
import com.unisubmit.repository.SubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Stage 1 mechanical pre-filter tests — the exclusion rules that make
 * collaboration the OPPOSITE of the similarity engine, plus the vector/set math.
 * No Spring context.
 */
class CollaborationDiscoveryServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;
    @Mock
    private CollaborationMatchRepository matchRepository;
    @Mock
    private CollaborationAssessmentService assessmentService;
    @Mock
    private CollaborationRequestService requestService;

    private CollaborationDiscoveryService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new CollaborationDiscoveryService(submissionRepository, matchRepository,
                new CollaborationWeights(), assessmentService, requestService);
        when(matchRepository.findByPair(any(), any())).thenReturn(Optional.empty());
        when(matchRepository.findBySubmission(any())).thenReturn(List.of());
    }

    // ── Vector / set math ────────────────────────────────────────────────────

    @Test
    void cosineOfIdenticalVectorsIsOne() {
        float[] v = {1f, 2f, 3f};
        assertEquals(1.0, CollaborationDiscoveryService.cosine(v, v.clone()), 1e-9);
    }

    @Test
    void cosineClampsAntiCorrelationToZero() {
        assertEquals(0.0, CollaborationDiscoveryService.cosine(new float[]{1f, 0f}, new float[]{-1f, 0f}), 1e-9);
    }

    @Test
    void cosineOfMismatchedOrNullIsZero() {
        assertEquals(0.0, CollaborationDiscoveryService.cosine(null, new float[]{1f}), 1e-9);
        assertEquals(0.0, CollaborationDiscoveryService.cosine(new float[]{1f, 2f}, new float[]{1f}), 1e-9);
    }

    @Test
    void jaccardMatchesHandComputedOverlap() {
        Set<String> a = new LinkedHashSet<>(List.of("transportation", "energy"));
        Set<String> b = new LinkedHashSet<>(List.of("transportation", "healthcare"));
        // intersection 1 (transportation), union 3 → 1/3
        assertEquals(1.0 / 3.0, CollaborationDiscoveryService.jaccard(a, b), 1e-9);
    }

    @Test
    void jaccardIsZeroWhenEitherSideEmpty() {
        assertEquals(0.0, CollaborationDiscoveryService.jaccard(Set.of(), Set.of("x")), 1e-9);
    }

    @Test
    void insightHashIsStableAndOrderIndependent() {
        Submission s1 = submission(1L, 100L, 10L, 1L, List.of("energy", "transportation"),
                SubmissionStatus.SUBMITTED, 3, true);
        Submission s2 = submission(1L, 100L, 10L, 1L, List.of("transportation", "energy"),
                SubmissionStatus.SUBMITTED, 3, true);
        assertEquals(CollaborationDiscoveryService.insightHash(s1),
                CollaborationDiscoveryService.insightHash(s2));
    }

    // ── B6a: complementarity ─────────────────────────────────────────────────

    @Test
    void complementPairOutranksSameToolkitOverlapPair() {
        // Same problem space, DIFFERENT toolkit → the pairing this engine exists to find.
        Submission current = submission(1L, 100L, 10L, 1L, List.of("health"),
                SubmissionStatus.SUBMITTED, 3, true);
        withTech(current, "Python");

        Submission complement = submission(2L, 200L, 20L, 2L, List.of("health"),
                SubmissionStatus.SUBMITTED, 3, true);
        withTech(complement, "Fieldwork Surveys");

        // Same toolkit, NO shared problem space → a twin, not a complement.
        Submission overlap = submission(3L, 300L, 30L, 2L, List.of(),
                SubmissionStatus.SUBMITTED, 3, true);
        withTech(overlap, "Python");

        when(submissionRepository.findAll()).thenReturn(List.of(current, complement, overlap));

        service.precomputeForSubmission(current);

        List<CollaborationMatch> saved = captureSaves();
        CollaborationMatch complementMatch = matchWithPartner(saved, 1L, 2L);
        CollaborationMatch overlapMatch = matchWithPartner(saved, 1L, 3L);

        assertTrue(complementMatch != null && overlapMatch != null, "both pairs should be shortlisted");
        assertEquals(MatchReasonType.COMPLEMENT, complementMatch.getReasonType());
        assertEquals(MatchReasonType.OVERLAP, overlapMatch.getReasonType());
        assertTrue(complementMatch.getMechanicalScore() > overlapMatch.getMechanicalScore(),
                "same-problem/different-toolkit must outrank a same-toolkit twin");

        // The reason names both sides of the gap.
        assertEquals("health", complementMatch.getReasonDomain());
        assertTrue(complementMatch.getReasonAItem() != null && complementMatch.getReasonBItem() != null,
                "complement reason must name what each side brings");
    }

    private static void withTech(Submission s, String... names) {
        for (String n : names) {
            com.unisubmit.domain.Technology t = new com.unisubmit.domain.Technology();
            t.setName(n);
            s.getTechnologies().add(t);
        }
    }

    private static CollaborationMatch matchWithPartner(List<CollaborationMatch> saved,
                                                       long selfId, long partnerId) {
        return saved.stream().filter(m -> partnerId(m, selfId) == partnerId).findFirst().orElse(null);
    }

    // ── Exclusion rules ──────────────────────────────────────────────────────

    @Test
    void crossDepartmentPartnerSharingDomainIsShortlisted() {
        Submission current = submission(1L, 100L, 10L, 1L, List.of("transportation"),
                SubmissionStatus.SUBMITTED, 3, true);
        Submission partner = submission(2L, 200L, 20L, 2L, List.of("transportation"),
                SubmissionStatus.APPROVED, 4, true);
        when(submissionRepository.findAll()).thenReturn(List.of(current, partner));

        service.precomputeForSubmission(current);

        List<CollaborationMatch> saved = captureSaves();
        assertEquals(1, saved.size());
        assertEquals(2L, partnerId(saved.get(0), 1L));
    }

    @Test
    void sameUnitClassmateIsExcluded() {
        Submission current = submission(1L, 100L, 10L, 1L, List.of("transportation"),
                SubmissionStatus.SUBMITTED, 3, true);
        // same unit (10) even though different student & department-shared topic
        Submission classmate = submission(2L, 200L, 10L, 1L, List.of("transportation"),
                SubmissionStatus.SUBMITTED, 3, true);
        when(submissionRepository.findAll()).thenReturn(List.of(current, classmate));

        service.precomputeForSubmission(current);

        verify(matchRepository, never()).save(any());
    }

    @Test
    void sameStudentOtherProjectIsExcluded() {
        Submission current = submission(1L, 100L, 10L, 1L, List.of("energy"),
                SubmissionStatus.SUBMITTED, 3, true);
        Submission ownOther = submission(2L, 100L, 20L, 2L, List.of("energy"),
                SubmissionStatus.SUBMITTED, 3, true);
        when(submissionRepository.findAll()).thenReturn(List.of(current, ownOther));

        service.precomputeForSubmission(current);

        verify(matchRepository, never()).save(any());
    }

    @Test
    void optedOutPartnerIsExcluded() {
        Submission current = submission(1L, 100L, 10L, 1L, List.of("healthcare"),
                SubmissionStatus.SUBMITTED, 3, true);
        Submission optedOut = submission(2L, 200L, 20L, 2L, List.of("healthcare"),
                SubmissionStatus.APPROVED, 4, /*discoverable*/ false);
        when(submissionRepository.findAll()).thenReturn(List.of(current, optedOut));

        service.precomputeForSubmission(current);

        verify(matchRepository, never()).save(any());
    }

    @Test
    void optedOutViewerProducesNoMatchesAndClearsUnassessed() {
        Submission current = submission(1L, 100L, 10L, 1L, List.of("healthcare"),
                SubmissionStatus.SUBMITTED, 3, /*discoverable*/ false);
        CollaborationMatch stale = new CollaborationMatch();
        stale.setSubmissionA(current);
        stale.setSubmissionB(submission(2L, 200L, 20L, 2L, List.of("healthcare"),
                SubmissionStatus.APPROVED, 4, true));
        when(matchRepository.findBySubmission(current)).thenReturn(new ArrayList<>(List.of(stale)));

        service.precomputeForSubmission(current);

        verify(matchRepository, never()).save(any());
        verify(matchRepository).delete(stale); // UNASSESSED stale row removed
    }

    @Test
    void nonAnalysedPartnerIsExcluded() {
        Submission current = submission(1L, 100L, 10L, 1L, List.of("energy"),
                SubmissionStatus.SUBMITTED, 3, true);
        Submission notAnalysed = submission(2L, 200L, 20L, 2L, List.of("energy"),
                SubmissionStatus.SUBMITTED, 3, true);
        notAnalysed.getAiInsight().setStatus(AIInsightStatus.PENDING); // not COMPLETED
        when(submissionRepository.findAll()).thenReturn(List.of(current, notAnalysed));

        service.precomputeForSubmission(current);

        verify(matchRepository, never()).save(any());
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private List<CollaborationMatch> captureSaves() {
        ArgumentCaptor<CollaborationMatch> captor = ArgumentCaptor.forClass(CollaborationMatch.class);
        verify(matchRepository, org.mockito.Mockito.atLeast(0)).save(captor.capture());
        return captor.getAllValues();
    }

    private static long partnerId(CollaborationMatch m, long selfId) {
        return m.getSubmissionA().getId() == selfId ? m.getSubmissionB().getId() : m.getSubmissionA().getId();
    }

    private Submission submission(Long id, Long studentId, Long unitId, Long deptId,
                                  List<String> domains, SubmissionStatus status,
                                  Integer year, boolean discoverable) {
        Department dept = new Department();
        dept.setId(deptId);

        Unit unit = new Unit();
        unit.setId(unitId);
        unit.setDepartment(dept);

        Course programme = new Course();
        programme.setId(deptId * 10);
        programme.setDepartment(dept);

        Curriculum curriculum = new Curriculum();
        curriculum.setId(unitId * 100);
        curriculum.setUnit(unit);
        curriculum.setProgramme(programme);

        StudentProfile profile = new StudentProfile();
        profile.setId(studentId + 1);
        profile.setCurrentYear(year);
        profile.setDiscoverableForCollaboration(discoverable);

        User student = new User();
        student.setId(studentId);
        student.setName("Student " + studentId);
        student.setRole(Role.STUDENT);
        student.setStudentProfile(profile);

        Submission s = new Submission();
        s.setId(id);
        s.setTitle("Project " + id);
        s.setStudent(student);
        s.setCurriculum(curriculum);
        s.setStatus(status);

        AIInsight insight = new AIInsight();
        insight.setSubmission(s);
        insight.setStatus(AIInsightStatus.COMPLETED);
        insight.setSummary("Summary " + id);
        insight.setProblemDomains(new LinkedHashSet<>(domains));
        s.setAiInsight(insight);
        return s;
    }
}
