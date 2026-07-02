package com.unisubmit.controller;

import com.unisubmit.domain.AIInsight;
import com.unisubmit.repository.AIInsightRepository;
import com.unisubmit.security.CustomUserDetails;
import com.unisubmit.service.AIInsightService;
import com.unisubmit.service.SubmissionAccessService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * Lightweight REST API used by the polling JavaScript in layout.html.
 * <p>
 * GET  /api/ai-insights/{id}         — returns current insight state (status, summary, keywords)
 * POST /api/ai-insights/{id}/retry   — re-triggers analysis for a FAILED insight
 * <p>
 * Both endpoints enforce {@link SubmissionAccessService} rules and answer 404
 * (never 403) when access is denied, so insight IDs cannot be probed.
 */
@RestController
public class AIInsightApiController {

    private final AIInsightRepository aiInsightRepository;
    private final AIInsightService aiInsightService;
    private final SubmissionAccessService accessService;

    public AIInsightApiController(AIInsightRepository aiInsightRepository,
                                  AIInsightService aiInsightService,
                                  SubmissionAccessService accessService) {
        this.aiInsightRepository = aiInsightRepository;
        this.aiInsightService = aiInsightService;
        this.accessService = accessService;
    }

    @GetMapping("/api/ai-insights/{id}")
    public ResponseEntity<AIInsight> getAiInsight(@PathVariable Long id,
                                                  @AuthenticationPrincipal CustomUserDetails userDetails) {
        Optional<AIInsight> insight = aiInsightRepository.findById(id);
        if (insight.isEmpty() || userDetails == null
                || !accessService.canDiscoverSubmission(userDetails.getUser(), insight.get().getSubmission())) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(insight.get());
    }

    /**
     * Retry endpoint: only succeeds if the insight is in FAILED state.
     * Returns 409 Conflict if the insight is not retriable.
     */
    @PostMapping("/api/ai-insights/{id}/retry")
    public ResponseEntity<Map<String, String>> retryInsight(@PathVariable Long id,
                                                            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Optional<AIInsight> insight = aiInsightRepository.findById(id);
        if (insight.isEmpty() || userDetails == null
                || !accessService.canAccessSubmissionFile(userDetails.getUser(), insight.get().getSubmission())) {
            return ResponseEntity.notFound().build();
        }
        boolean started = aiInsightService.retryAnalysis(id);
        if (started) {
            return ResponseEntity.ok(Map.of("status", "PENDING", "message", "Analysis restarted"));
        } else {
            return ResponseEntity.status(409)
                    .body(Map.of("status", "ERROR", "message", "Insight is not in a FAILED state"));
        }
    }
}
