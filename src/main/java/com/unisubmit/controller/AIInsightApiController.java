package com.unisubmit.controller;

import com.unisubmit.domain.AIInsight;
import com.unisubmit.domain.AIInsightStatus;
import com.unisubmit.repository.AIInsightRepository;
import com.unisubmit.service.AIInsightService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * Lightweight REST API used by the polling JavaScript in layout.html.
 * <p>
 * GET  /api/ai-insights/{id}         — returns current insight state (status, summary, keywords)
 * POST /api/ai-insights/{id}/retry   — re-triggers analysis for a FAILED insight
 */
@RestController
public class AIInsightApiController {

    private final AIInsightRepository aiInsightRepository;
    private final AIInsightService aiInsightService;

    public AIInsightApiController(AIInsightRepository aiInsightRepository,
                                  AIInsightService aiInsightService) {
        this.aiInsightRepository = aiInsightRepository;
        this.aiInsightService = aiInsightService;
    }

    @GetMapping("/api/ai-insights/{id}")
    public ResponseEntity<AIInsight> getAiInsight(@PathVariable Long id) {
        return aiInsightRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Retry endpoint: only succeeds if the insight is in FAILED state.
     * Returns 409 Conflict if the insight is not retriable.
     */
    @PostMapping("/api/ai-insights/{id}/retry")
    public ResponseEntity<Map<String, String>> retryInsight(@PathVariable Long id) {
        boolean started = aiInsightService.retryAnalysis(id);
        if (started) {
            return ResponseEntity.ok(Map.of("status", "PENDING", "message", "Analysis restarted"));
        } else {
            return ResponseEntity.status(409)
                    .body(Map.of("status", "ERROR", "message", "Insight is not in a FAILED state"));
        }
    }
}
