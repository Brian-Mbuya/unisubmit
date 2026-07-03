package com.unisubmit.service;

import com.unisubmit.domain.AIInsight;
import com.unisubmit.domain.AIInsightStatus;
import com.unisubmit.domain.Role;
import com.unisubmit.domain.Submission;
import com.unisubmit.domain.SubmissionStatus;
import com.unisubmit.domain.User;
import com.unisubmit.repository.SubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * BM25 ranking and visibility tests for the hybrid search — no Spring
 * context; the semantic channel stays disabled (its default), so these
 * exercise the keyword path that runs on every database.
 */
class SearchServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private SubmissionAccessService accessService;

    @Mock
    private SpecterService specterService;

    private SearchService searchService;
    private User viewer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        searchService = new SearchService(submissionRepository, accessService, specterService, null);
        viewer = new User();
        viewer.setId(1L);
        viewer.setRole(Role.STUDENT);
    }

    @Test
    void blankQueryReturnsNothing() {
        assertTrue(searchService.search("   ", viewer).isEmpty());
    }

    @Test
    void documentsMatchingTheQueryOutrankNonMatching() {
        Submission traffic = submission(10L, "Traffic Prediction System",
                "Deep learning model forecasting urban traffic congestion.",
                List.of("traffic", "forecasting"));
        Submission farming = submission(11L, "Smart Farming Platform",
                "IoT sensors for greenhouse automation.",
                List.of("iot", "agriculture"));
        stubCorpus(traffic, farming);

        List<SearchService.SearchHit> hits = searchService.search("traffic forecasting", viewer);

        assertEquals(1, hits.size(), "only the matching document should be retrieved");
        assertEquals(10L, hits.get(0).submission().getId());
    }

    @Test
    void rarerTermsWeighMoreThanUbiquitousOnes() {
        // "system" appears everywhere; "quantum" only in one document.
        Submission quantum = submission(10L, "Quantum System",
                "A study of quantum computation.", List.of("quantum"));
        Submission generic1 = submission(11L, "Library System",
                "A generic system for libraries.", List.of("library"));
        Submission generic2 = submission(12L, "Booking System",
                "A generic system for bookings.", List.of("booking"));
        stubCorpus(quantum, generic1, generic2);

        List<SearchService.SearchHit> hits = searchService.search("quantum system", viewer);

        assertEquals(10L, hits.get(0).submission().getId(),
                "the document with the rare term must rank first");
    }

    @Test
    void titleMatchesOutrankSummaryOnlyMatches() {
        Submission inTitle = submission(10L, "Blockchain Voting",
                "An electoral platform.", List.of());
        Submission inSummary = submission(11L, "Secure Elections",
                "Uses blockchain voting ideas in passing.", List.of());
        stubCorpus(inTitle, inSummary);

        List<SearchService.SearchHit> hits = searchService.search("blockchain voting", viewer);

        assertEquals(2, hits.size());
        assertEquals(10L, hits.get(0).submission().getId());
    }

    @Test
    void undiscoverableSubmissionsNeverAppear() {
        Submission visible = submission(10L, "Traffic Prediction", "Traffic.", List.of("traffic"));
        Submission hidden = submission(11L, "Traffic Prediction Draft", "Traffic.", List.of("traffic"));
        when(submissionRepository.findAll()).thenReturn(List.of(visible, hidden));
        when(accessService.canDiscoverSubmission(viewer, visible)).thenReturn(true);
        when(accessService.canDiscoverSubmission(viewer, hidden)).thenReturn(false);

        List<SearchService.SearchHit> hits = searchService.search("traffic", viewer);

        assertEquals(1, hits.size());
        assertEquals(10L, hits.get(0).submission().getId());
    }

    @Test
    void tokenizeLowercasesAndDropsShortTokens() {
        assertEquals(List.of("machine", "learning", "c#"),
                SearchService.tokenize("Machine LEARNING, a C#!"));
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private void stubCorpus(Submission... submissions) {
        when(submissionRepository.findAll()).thenReturn(List.of(submissions));
        when(accessService.canDiscoverSubmission(any(User.class), any(Submission.class))).thenReturn(true);
    }

    private Submission submission(Long id, String title, String summary, List<String> keywords) {
        User owner = new User();
        owner.setId(id + 500);
        owner.setName("Owner " + id);
        owner.setRole(Role.STUDENT);

        Submission s = new Submission();
        s.setId(id);
        s.setTitle(title);
        s.setStudent(owner);
        s.setStatus(SubmissionStatus.SUBMITTED);

        AIInsight insight = new AIInsight();
        insight.setSubmission(s);
        insight.setStatus(AIInsightStatus.COMPLETED);
        insight.setSummary(summary);
        insight.setKeywords(new LinkedHashSet<>(keywords));
        s.setAiInsight(insight);
        return s;
    }
}
