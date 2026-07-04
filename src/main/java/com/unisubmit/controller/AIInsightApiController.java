package com.unisubmit.controller;

import com.unisubmit.domain.AIInsight;
import com.unisubmit.domain.Submission;
import com.unisubmit.repository.AIInsightRepository;
import com.unisubmit.repository.SubmissionRepository;
import com.unisubmit.security.CustomUserDetails;
import com.unisubmit.service.AIInsightService;
import com.unisubmit.service.AIInsightProcessingService;
import com.unisubmit.service.SubmissionAccessService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lightweight REST API used by the polling JavaScript in layout.html.
 * <p>
 * GET  /api/ai-insights/{id}                 — returns current insight state
 * POST /api/ai-insights/{id}/retry           — re-triggers analysis for a FAILED insight
 * GET  /api/ai/suggest-title/{submissionId}  — generates 3 AI title suggestions
 * POST /api/ai/rename/{submissionId}         — applies a chosen title to a submission
 */
@RestController
public class AIInsightApiController {

    private final AIInsightRepository aiInsightRepository;
    private final AIInsightService aiInsightService;
    private final AIInsightProcessingService aiProcessingService;
    private final SubmissionAccessService accessService;
    private final SubmissionRepository submissionRepository;

    public AIInsightApiController(AIInsightRepository aiInsightRepository,
                                  AIInsightService aiInsightService,
                                  AIInsightProcessingService aiProcessingService,
                                  SubmissionAccessService accessService,
                                  SubmissionRepository submissionRepository) {
        this.aiInsightRepository = aiInsightRepository;
        this.aiInsightService = aiInsightService;
        this.aiProcessingService = aiProcessingService;
        this.accessService = accessService;
        this.submissionRepository = submissionRepository;
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
     * Suggest 3 AI-generated project titles based on the AI analysis.
     * Requires the submission to have a COMPLETED AI insight.
     */
    @GetMapping("/api/ai/suggest-title/{submissionId}")
    public ResponseEntity<Map<String, Object>> suggestTitle(
            @PathVariable Long submissionId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Optional<Submission> sub = submissionRepository.findById(submissionId);
        if (sub.isEmpty() || userDetails == null
                || !accessService.canAccessSubmissionFile(userDetails.getUser(), sub.get())) {
            return ResponseEntity.notFound().build();
        }
        if (sub.get().getAiInsight() == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "AI analysis has not completed for this submission"));
        }

        List<String> suggestions = aiProcessingService.suggestTitles(submissionId);
        if (suggestions.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "suggestions", List.of(),
                    "message", "Could not generate suggestions. Check that an AI API key is configured."
            ));
        }
        return ResponseEntity.ok(Map.of("suggestions", suggestions));
    }

    /**
     * Rename a submission to a chosen AI-suggested title.
     */
    @PostMapping("/api/ai/rename/{submissionId}")
    public ResponseEntity<Map<String, String>> renameSubmission(
            @PathVariable Long submissionId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Optional<Submission> sub = submissionRepository.findById(submissionId);
        if (sub.isEmpty() || userDetails == null
                || !accessService.canAccessSubmissionFile(userDetails.getUser(), sub.get())) {
            return ResponseEntity.notFound().build();
        }
        String newTitle = body.get("title");
        if (newTitle == null || newTitle.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Title cannot be empty"));
        }

        Submission submission = sub.get();
        submission.setTitle(newTitle.trim());
        submissionRepository.save(submission);
        return ResponseEntity.ok(Map.of("status", "OK", "title", newTitle.trim()));
    }
}
