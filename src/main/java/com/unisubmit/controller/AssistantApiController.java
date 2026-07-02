package com.unisubmit.controller;

import com.unisubmit.domain.Submission;
import com.unisubmit.repository.SubmissionRepository;
import com.unisubmit.security.CustomUserDetails;
import com.unisubmit.service.AssistantService;
import com.unisubmit.service.SubmissionAccessService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * Phase 6 — Explainable Academic Assistant endpoints.
 * <p>
 * POST /api/assistant/{submissionId}/explain — natural-language explanation of
 * the precomputed similarity/reviewer data.
 * POST /api/assistant/{submissionId}/ask — Q&A scoped to the submission's own
 * document.
 * <p>
 * Both are guarded by {@link SubmissionAccessService#canDiscoverSubmission}
 * (404 on failure so IDs cannot be probed) and a shared in-memory rate limit
 * of {@value AssistantService#RATE_LIMIT_PER_HOUR} calls per submission per
 * hour (429 when exceeded).
 */
@RestController
@RequestMapping("/api/assistant")
public class AssistantApiController {

    private final AssistantService assistantService;
    private final SubmissionRepository submissionRepository;
    private final SubmissionAccessService accessService;

    public AssistantApiController(AssistantService assistantService,
                                  SubmissionRepository submissionRepository,
                                  SubmissionAccessService accessService) {
        this.assistantService = assistantService;
        this.submissionRepository = submissionRepository;
        this.accessService = accessService;
    }

    @PostMapping("/{submissionId}/explain")
    public ResponseEntity<Map<String, Object>> explain(@PathVariable Long submissionId,
                                                       @AuthenticationPrincipal CustomUserDetails userDetails) {
        Submission submission = accessibleSubmission(submissionId, userDetails);
        if (submission == null) {
            return ResponseEntity.notFound().build();
        }
        if (!assistantService.tryConsume(submissionId)) {
            return rateLimited();
        }
        AssistantService.AssistantReply reply =
                assistantService.explain(submission, userDetails.getUser());
        return ResponseEntity.ok(Map.of("available", reply.available(), "text", reply.text()));
    }

    @PostMapping("/{submissionId}/ask")
    public ResponseEntity<Map<String, Object>> ask(@PathVariable Long submissionId,
                                                   @RequestBody Map<String, String> body,
                                                   @AuthenticationPrincipal CustomUserDetails userDetails) {
        Submission submission = accessibleSubmission(submissionId, userDetails);
        if (submission == null) {
            return ResponseEntity.notFound().build();
        }
        if (!assistantService.tryConsume(submissionId)) {
            return rateLimited();
        }
        AssistantService.AssistantReply reply =
                assistantService.ask(submission, body == null ? null : body.get("question"));
        return ResponseEntity.ok(Map.of("available", reply.available(), "text", reply.text()));
    }

    /** 404 (not 403) on missing OR forbidden — indistinguishable to a prober. */
    private Submission accessibleSubmission(Long submissionId, CustomUserDetails userDetails) {
        if (userDetails == null) {
            return null;
        }
        Optional<Submission> submission = submissionRepository.findById(submissionId);
        if (submission.isEmpty()
                || !accessService.canDiscoverSubmission(userDetails.getUser(), submission.get())) {
            return null;
        }
        return submission.get();
    }

    private ResponseEntity<Map<String, Object>> rateLimited() {
        return ResponseEntity.status(429).body(Map.of(
                "available", false,
                "text", "The assistant is taking a short break — this submission has "
                        + "reached its hourly limit of assistant calls. Try again later."));
    }
}
