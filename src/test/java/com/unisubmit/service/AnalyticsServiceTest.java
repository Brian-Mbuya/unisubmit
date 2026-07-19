package com.unisubmit.service;

import com.unisubmit.domain.AIInsight;
import com.unisubmit.domain.AIInsightStatus;
import com.unisubmit.domain.Submission;
import com.unisubmit.repository.SubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

/**
 * The dimension guard on {@link AnalyticsService#buildLandscape()} (4.4): a corpus with mixed
 * embedding sizes (old 768-d SPECTER vectors next to new 1536-d ones) must not blow up kMeans/
 * PCA — the majority dimension wins and the rest are dropped.
 */
class AnalyticsServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;

    private AnalyticsService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new AnalyticsService(submissionRepository);
    }

    @Test
    void buildLandscapeSurvivesMixedEmbeddingDimensions() {
        List<Submission> subs = new ArrayList<>();
        for (int i = 0; i < 4; i++) subs.add(sub(100L + i, 1536)); // majority
        for (int i = 0; i < 2; i++) subs.add(sub(200L + i, 768));  // minority — dropped
        when(submissionRepository.findAll()).thenReturn(subs);

        AnalyticsService.Landscape landscape = service.buildLandscape();

        assertNotNull(landscape);
        // Only the 4 majority-dimension submissions are plotted; the 768-d pair is filtered out.
        assertEquals(4, landscape.points().size());
    }

    private Submission sub(Long id, int dim) {
        Submission s = new Submission();
        s.setId(id);
        s.setTitle("Project " + id);

        AIInsight insight = new AIInsight();
        insight.setStatus(AIInsightStatus.COMPLETED);
        insight.setSummary("summary " + id);
        insight.setKeywords(new LinkedHashSet<>(List.of("alpha", "beta")));
        s.setAiInsight(insight);

        float[] embedding = new float[dim];
        for (int j = 0; j < dim; j++) {
            embedding[j] = (float) ((id + j) % 7) / 7f;
        }
        s.setEmbedding(embedding);
        return s;
    }
}
