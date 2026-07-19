package com.unisubmit.controller;

import com.unisubmit.domain.AIInsight;
import com.unisubmit.repository.AIInsightRepository;
import com.unisubmit.security.CustomUserDetails;
import com.unisubmit.service.AIInsightService;
import com.unisubmit.service.AIInsightProcessingService;
import com.unisubmit.service.SubmissionAccessService;
import com.unisubmit.service.ai.AiRateLimitService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lightweight REST API used by the polling JavaScript in layout.html.
 * <p>
 * GET  /api/ai-insights/{id}          — returns current insight state
 * POST /api/ai-insights/{id}/retry    — re-triggers analysis for a FAILED insight
 * POST /api/ai/analyze-draft-file     — 3 title suggestions for an unsaved draft file
 */
@RestController
public class AIInsightApiController {

    private final AIInsightRepository aiInsightRepository;
    private final AIInsightService aiInsightService;
    private final AIInsightProcessingService aiProcessingService;
    private final SubmissionAccessService accessService;
    private final AiRateLimitService rateLimitService;

    public AIInsightApiController(AIInsightRepository aiInsightRepository,
                                  AIInsightService aiInsightService,
                                  AIInsightProcessingService aiProcessingService,
                                  SubmissionAccessService accessService,
                                  AiRateLimitService rateLimitService) {
        this.aiInsightRepository = aiInsightRepository;
        this.aiInsightService = aiInsightService;
        this.aiProcessingService = aiProcessingService;
        this.accessService = accessService;
        this.rateLimitService = rateLimitService;
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

    /**
     * Stateless endpoint: analyzes a chosen draft file from the student's local computer
     * and suggests 3 creative titles before the project submission is officially created.
     * Rate-limited per user (DRAFT_TITLES bucket) to keep provider costs bounded.
     */
    @PostMapping("/api/ai/analyze-draft-file")
    public ResponseEntity<Map<String, Object>> analyzeDraftFile(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty or not provided"));
        }
        if (userDetails != null && !rateLimitService.tryConsume(
                userDetails.getUser().getId(), AiRateLimitService.Bucket.DRAFT_TITLES)) {
            return ResponseEntity.status(429).body(Map.of(
                    "error", "You've asked for title suggestions several times — please wait a little and try again."));
        }
        List<String> suggestions = aiProcessingService.suggestTitlesForDraft(file);
        if (suggestions.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "suggestions", List.of(),
                    "message", "No suggestions this time — give your project a name and continue."
            ));
        }
        return ResponseEntity.ok(Map.of("suggestions", suggestions));
    }
}
