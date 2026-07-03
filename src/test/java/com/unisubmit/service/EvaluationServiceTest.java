package com.unisubmit.service;

import com.unisubmit.config.RecommendationWeights;
import com.unisubmit.domain.CollaborationRequest;
import com.unisubmit.domain.CollaborationRequestStatus;
import com.unisubmit.domain.Role;
import com.unisubmit.domain.Submission;
import com.unisubmit.domain.User;
import com.unisubmit.repository.CollaborationRequestRepository;
import com.unisubmit.repository.SubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Metric-math tests for the evaluation harness — the ranker itself is
 * stubbed, so precision@5 and MRR arithmetic is exercised in isolation.
 */
class EvaluationServiceTest {

    @Mock
    private CollaborationRequestRepository requestRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private RecommendationService recommendationService;

    private EvaluationService evaluationService;

    private User requester;
    private Submission ownSubmission;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        evaluationService = new EvaluationService(requestRepository, submissionRepository,
                recommendationService, new RecommendationWeights());

        requester = new User();
        requester.setId(1L);
        requester.setName("Requester");
        requester.setRole(Role.STUDENT);

        ownSubmission = new Submission();
        ownSubmission.setId(100L);
        ownSubmission.setStudent(requester);
    }

    @Test
    void noGroundTruthYieldsEmptyMetricsForEveryConfig() {
        when(requestRepository.findByStatus(CollaborationRequestStatus.ACCEPTED)).thenReturn(List.of());

        EvaluationService.EvaluationReport report = evaluationService.evaluate();

        assertEquals(0, report.groundTruthPairs());
        assertTrue(report.results().size() >= 5, "expected several weight configurations");
        report.results().forEach(r -> {
            assertEquals(0.0, r.precisionAtK(), 1e-9);
            assertEquals(0.0, r.mrr(), 1e-9);
        });
    }

    @Test
    void targetRankedFirstScoresPerfectly() {
        stubAcceptedRequest(200L);
        when(recommendationService.rankCandidateIds(eq(ownSubmission), any(RecommendationWeights.class)))
                .thenReturn(List.of(200L, 300L, 400L));

        EvaluationService.EvaluationReport report = evaluationService.evaluate();

        EvaluationService.ConfigResult current = report.results().get(0);
        assertEquals(1, current.pairs());
        assertEquals(1, current.retrieved());
        assertEquals(1.0, current.precisionAtK(), 1e-9);
        assertEquals(1.0, current.mrr(), 1e-9);
    }

    @Test
    void targetOutsideTopFiveCountsForMrrButNotPrecision() {
        stubAcceptedRequest(200L);
        // rank 6 (index 5)
        when(recommendationService.rankCandidateIds(eq(ownSubmission), any(RecommendationWeights.class)))
                .thenReturn(List.of(1L, 2L, 3L, 4L, 5L, 200L));

        EvaluationService.EvaluationReport report = evaluationService.evaluate();

        EvaluationService.ConfigResult current = report.results().get(0);
        assertEquals(0.0, current.precisionAtK(), 1e-9);
        assertEquals(1.0 / 6.0, current.mrr(), 1e-9);
        assertEquals(1, current.retrieved());
    }

    @Test
    void targetNeverRetrievedScoresZero() {
        stubAcceptedRequest(200L);
        when(recommendationService.rankCandidateIds(eq(ownSubmission), any(RecommendationWeights.class)))
                .thenReturn(List.of(1L, 2L, 3L));

        EvaluationService.EvaluationReport report = evaluationService.evaluate();

        EvaluationService.ConfigResult current = report.results().get(0);
        assertEquals(1, current.pairs());
        assertEquals(0, current.retrieved());
        assertEquals(0.0, current.precisionAtK(), 1e-9);
        assertEquals(0.0, current.mrr(), 1e-9);
    }

    @Test
    void bestRankAcrossOwnSubmissionsIsUsed() {
        Submission second = new Submission();
        second.setId(101L);
        second.setStudent(requester);

        stubAcceptedRequest(200L);
        when(submissionRepository.findByStudent(requester)).thenReturn(List.of(ownSubmission, second));
        // From submission 100 the target ranks 3rd; from 101 it ranks 1st → best = 1.
        when(recommendationService.rankCandidateIds(eq(ownSubmission), any(RecommendationWeights.class)))
                .thenReturn(List.of(9L, 8L, 200L));
        when(recommendationService.rankCandidateIds(eq(second), any(RecommendationWeights.class)))
                .thenReturn(List.of(200L, 9L));

        EvaluationService.EvaluationReport report = evaluationService.evaluate();

        assertEquals(1.0, report.results().get(0).mrr(), 1e-9);
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private void stubAcceptedRequest(Long targetSubmissionId) {
        Submission target = new Submission();
        target.setId(targetSubmissionId);

        CollaborationRequest accepted = new CollaborationRequest();
        accepted.setId(1L);
        accepted.setSender(requester);
        accepted.setSubmission(target);
        accepted.setStatus(CollaborationRequestStatus.ACCEPTED);

        when(requestRepository.findByStatus(CollaborationRequestStatus.ACCEPTED))
                .thenReturn(List.of(accepted));
        when(submissionRepository.findByStudent(requester)).thenReturn(List.of(ownSubmission));
    }
}
