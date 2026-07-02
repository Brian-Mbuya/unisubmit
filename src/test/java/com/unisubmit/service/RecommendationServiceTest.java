package com.unisubmit.service;

import com.unisubmit.config.RecommendationWeights;
import com.unisubmit.domain.AIInsight;
import com.unisubmit.domain.AIInsightStatus;
import com.unisubmit.domain.Curriculum;
import com.unisubmit.domain.Department;
import com.unisubmit.domain.ResearchArea;
import com.unisubmit.domain.Role;
import com.unisubmit.domain.Submission;
import com.unisubmit.domain.SubmissionSimilarity;
import com.unisubmit.domain.SubmissionStatus;
import com.unisubmit.domain.Technology;
import com.unisubmit.domain.Unit;
import com.unisubmit.domain.User;
import com.unisubmit.dto.SimilarSubmission;
import com.unisubmit.repository.SubmissionRepository;
import com.unisubmit.repository.SubmissionSimilarityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Pageable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure-math tests for the six-signal recommendation scoring — no Spring
 * context. Covers Jaccard edge cases, weight normalisation, display-casing
 * preservation, and viewer filtering with a stubbed access service.
 */
class RecommendationServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private SubmissionSimilarityRepository similarityRepository;

    @Mock
    private SubmissionAccessService accessService;

    private RecommendationWeights weights;
    private RecommendationService recommendationService;

    private User owner;
    private User otherStudent;
    private Unit sharedUnit;
    private Curriculum sharedCurriculum;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        weights = new RecommendationWeights(); // defaults from application.yml
        recommendationService = new RecommendationService(
                submissionRepository, similarityRepository, accessService, weights);

        Department department = new Department();
        department.setId(1L);

        sharedUnit = new Unit();
        sharedUnit.setId(10L);
        sharedUnit.setDepartment(department);

        sharedCurriculum = new Curriculum();
        sharedCurriculum.setId(20L);
        sharedCurriculum.setUnit(sharedUnit);

        owner = student(100L);
        otherStudent = student(101L);
    }

    // ── precompute: scoring math ─────────────────────────────────────────────

    @Test
    void weightedScoreIsNormalisedBySumOfWeights() {
        Submission current = submission(1L, owner, "Machine Learning Pipeline",
                Set.of("Java", "PostgreSQL"), Set.of("Machine Learning"), List.of("ml", "pipeline"));
        Submission candidate = submission(2L, otherStudent, "Deep Learning Pipeline",
                Set.of("java"), Set.of("machine learning"), List.of("ml"));

        SubmissionSimilarity saved = runPrecompute(current, candidate);

        // keyword 1/2, title 2/4, unit 1.0, semantic 0, tech 1/2, area 1/1
        assertEquals(0.5, saved.getKeywordScore(), 1e-9);
        assertEquals(0.5, saved.getTitleScore(), 1e-9);
        assertEquals(1.0, saved.getUnitScore(), 1e-9);
        assertEquals(0.0, saved.getSemanticScore(), 1e-9);
        assertEquals(0.5, saved.getTechnologyScore(), 1e-9);
        assertEquals(1.0, saved.getResearchAreaScore(), 1e-9);

        double expectedWeighted = 0.5 * weights.getKeyword()
                + 0.5 * weights.getTitle()
                + 1.0 * weights.getUnit()
                + 0.0 * weights.getSemantic()
                + 0.5 * weights.getTechnology()
                + 1.0 * weights.getResearchArea();
        assertEquals(expectedWeighted / weights.totalWeight(), saved.getSimilarityScore(), 1e-9);
        assertTrue(saved.getSimilarityScore() <= 1.0, "normalised score must stay in 0..1");
    }

    @Test
    void perfectOverlapOnEverySignalScoresExactlyOne() {
        Submission current = submission(1L, owner, "Smart Farming",
                Set.of("Python"), Set.of("IoT"), List.of("farming"));
        Submission candidate = submission(2L, otherStudent, "Smart Farming",
                Set.of("Python"), Set.of("IoT"), List.of("farming"));

        SubmissionSimilarity saved = runPrecompute(current, candidate);

        // Every signal except semantic is 1.0; semantic weight contributes 0.
        double expected = (weights.getKeyword() + weights.getTitle() + weights.getUnit()
                + weights.getTechnology() + weights.getResearchArea()) / weights.totalWeight();
        assertEquals(expected, saved.getSimilarityScore(), 1e-9);
    }

    @Test
    void jaccardIsZeroWhenEitherTagSetIsEmpty() {
        Submission current = submission(1L, owner, "Alpha",
                Set.of("Java"), Set.of(), List.of("alpha"));
        Submission candidate = submission(2L, otherStudent, "Alpha",
                Set.of(), Set.of(), List.of("alpha"));

        SubmissionSimilarity saved = runPrecompute(current, candidate);

        assertEquals(0.0, saved.getTechnologyScore(), 1e-9);
        assertEquals(0.0, saved.getResearchAreaScore(), 1e-9);
    }

    @Test
    void sharedTagsKeepOriginalDisplayCasing() {
        Submission current = submission(1L, owner, "Data Platform",
                Set.of("PostgreSQL", "Java"), Set.of(), List.of());
        Submission candidate = submission(2L, otherStudent, "Data Warehouse",
                Set.of("postgresql", "java"), Set.of(), List.of());

        SubmissionSimilarity saved = runPrecompute(current, candidate);

        // Overlap math is case-insensitive but chips must show the original
        // casing — "PostgreSQL", never "Postgresql".
        assertEquals(List.of("Java", "PostgreSQL"), saved.getMatchedTechnologies());
        assertEquals(1.0, saved.getTechnologyScore(), 1e-9);
    }

    @Test
    void candidatesTheOwnerMayNotDiscoverAreNeverScored() {
        Submission current = submission(1L, owner, "Alpha",
                Set.of("Java"), Set.of(), List.of("alpha"));
        Submission hiddenDraft = submission(2L, otherStudent, "Alpha",
                Set.of("Java"), Set.of(), List.of("alpha"));
        hiddenDraft.setStatus(SubmissionStatus.DRAFT);

        when(submissionRepository.findByCurriculum_UnitAndStudentNot(sharedUnit, owner))
                .thenReturn(List.of(hiddenDraft));
        when(submissionRepository.findByStudentNotOrderByCreatedAtDesc(any(User.class), any(Pageable.class)))
                .thenReturn(List.of());
        when(submissionRepository.findWithRecommendationDataByIdIn(anyCollection()))
                .thenReturn(List.of(hiddenDraft));
        when(accessService.canDiscoverSubmission(owner, hiddenDraft)).thenReturn(false);

        recommendationService.precomputeForSubmission(current);

        ArgumentCaptor<List<SubmissionSimilarity>> captor = ArgumentCaptor.forClass(List.class);
        verify(similarityRepository).saveAll(captor.capture());
        assertTrue(captor.getValue().isEmpty(), "private drafts must not produce similarity rows");
    }

    // ── read side: viewer filtering ──────────────────────────────────────────

    @Test
    void findSimilarSubmissionsFiltersOutWhatTheViewerMayNotDiscover() {
        Submission current = submission(1L, owner, "Alpha", Set.of(), Set.of(), List.of());
        Submission visible = submission(2L, otherStudent, "Beta", Set.of(), Set.of(), List.of());
        Submission hidden = submission(3L, otherStudent, "Gamma", Set.of(), Set.of(), List.of());

        when(similarityRepository.findBySubmissionOrderBySimilarityScoreDesc(current))
                .thenReturn(List.of(similarity(current, visible, 0.9),
                        similarity(current, hidden, 0.8)));
        when(accessService.canDiscoverSubmission(owner, visible)).thenReturn(true);
        when(accessService.canDiscoverSubmission(owner, hidden)).thenReturn(false);

        List<SimilarSubmission> results = recommendationService.findSimilarSubmissions(current, owner);

        assertEquals(1, results.size());
        assertEquals(2L, results.get(0).submission().getId());
    }

    @Test
    void matchLabelsFollowScoreThresholds() {
        Submission current = submission(1L, owner, "Alpha", Set.of(), Set.of(), List.of());
        Submission strong = submission(2L, otherStudent, "B", Set.of(), Set.of(), List.of());
        Submission related = submission(3L, otherStudent, "C", Set.of(), Set.of(), List.of());
        Submission weak = submission(4L, otherStudent, "D", Set.of(), Set.of(), List.of());

        when(similarityRepository.findBySubmissionOrderBySimilarityScoreDesc(current))
                .thenReturn(List.of(similarity(current, strong, 0.5),
                        similarity(current, related, 0.3),
                        similarity(current, weak, 0.1)));
        when(accessService.canDiscoverSubmission(any(), any())).thenReturn(true);

        List<SimilarSubmission> results = recommendationService.findSimilarSubmissions(current, owner);

        assertEquals("Strong match", results.get(0).matchLabel());
        assertEquals("Related work", results.get(1).matchLabel());
        assertEquals("Possible match", results.get(2).matchLabel());
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private SubmissionSimilarity runPrecompute(Submission current, Submission candidate) {
        when(submissionRepository.findByCurriculum_UnitAndStudentNot(sharedUnit, current.getStudent()))
                .thenReturn(List.of(candidate));
        when(submissionRepository.findByStudentNotOrderByCreatedAtDesc(any(User.class), any(Pageable.class)))
                .thenReturn(List.of());
        when(submissionRepository.findWithRecommendationDataByIdIn(anyCollection()))
                .thenReturn(List.of(candidate));
        when(accessService.canDiscoverSubmission(any(User.class), any(Submission.class)))
                .thenReturn(true);

        recommendationService.precomputeForSubmission(current);

        ArgumentCaptor<List<SubmissionSimilarity>> captor = ArgumentCaptor.forClass(List.class);
        verify(similarityRepository).saveAll(captor.capture());
        assertEquals(1, captor.getValue().size(), "expected exactly one similarity row");
        return captor.getValue().get(0);
    }

    private User student(Long id) {
        User user = new User();
        user.setId(id);
        user.setName("Student " + id);
        user.setRole(Role.STUDENT);
        return user;
    }

    private Submission submission(Long id, User student, String title,
                                  Set<String> technologies, Set<String> researchAreas,
                                  List<String> keywords) {
        Submission submission = new Submission();
        submission.setId(id);
        submission.setStudent(student);
        submission.setTitle(title);
        submission.setCurriculum(sharedCurriculum);
        submission.setStatus(SubmissionStatus.SUBMITTED);

        Set<Technology> techSet = new LinkedHashSet<>();
        long techId = 500 + id * 10;
        for (String name : technologies) {
            Technology tech = new Technology();
            tech.setId(techId++);
            tech.setName(name);
            techSet.add(tech);
        }
        submission.setTechnologies(techSet);

        Set<ResearchArea> areaSet = new LinkedHashSet<>();
        long areaId = 700 + id * 10;
        for (String name : researchAreas) {
            ResearchArea area = new ResearchArea();
            area.setId(areaId++);
            area.setName(name);
            areaSet.add(area);
        }
        submission.setResearchAreas(areaSet);

        if (!keywords.isEmpty()) {
            AIInsight insight = new AIInsight();
            insight.setSubmission(submission);
            insight.setStatus(AIInsightStatus.COMPLETED);
            insight.setKeywords(new LinkedHashSet<>(keywords));
            submission.setAiInsight(insight);
        }
        return submission;
    }

    private SubmissionSimilarity similarity(Submission a, Submission b, double score) {
        SubmissionSimilarity sim = new SubmissionSimilarity();
        sim.setSubmissionA(a);
        sim.setSubmissionB(b);
        sim.setSimilarityScore(score);
        sim.setMatchedKeywords(List.of());
        sim.setMatchedTechnologies(List.of());
        sim.setMatchedResearchAreas(List.of());
        return sim;
    }
}
