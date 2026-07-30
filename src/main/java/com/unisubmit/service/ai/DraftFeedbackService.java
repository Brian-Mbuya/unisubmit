package com.unisubmit.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.unisubmit.domain.Submission;
import com.unisubmit.domain.SubmissionVersion;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Drafts review feedback for a lecturer from the submission's own document. The lecturer always
 * edits and remains the author — nothing is ever auto-sent. Degrades to a friendly error string
 * (never an exception) when there is no key, no stored file, or the provider is unusable.
 */
@Service
public class DraftFeedbackService {

    /** How much of the document to feed the model — same cap the retired assistant used. */
    private static final int MAX_DOCUMENT_CHARS = 8000;

    private final LlmClient llmClient;
    private final Path uploadRoot;

    public DraftFeedbackService(LlmClient llmClient,
                                @Value("${app.storage.upload-dir:uploads}") String uploadDir) {
        this.llmClient = llmClient;
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    /** Either a draft or a user-facing error — exactly one is non-null. */
    public record DraftResult(String draft, String error) {}

    public DraftResult draftFor(Submission submission, String selectedStatus) {
        if (!llmClient.hasKey()) {
            return new DraftResult(null, "AI drafting isn't configured on this server.");
        }
        if (submission == null || submission.getVersions() == null || submission.getVersions().isEmpty()) {
            return new DraftResult(null,
                    "The file for this version is no longer stored — ask for a re-upload before drafting.");
        }

        SubmissionVersion latest = submission.getVersions().get(submission.getVersions().size() - 1);
        String text = llmClient.extractCapped(uploadRoot.resolve(latest.getFilePath()), MAX_DOCUMENT_CHARS);
        if (text == null || text.isBlank()) {
            return new DraftResult(null,
                    "The file for this version is no longer stored — ask for a re-upload before drafting.");
        }

        String decision = (selectedStatus == null || selectedStatus.isBlank())
                ? "not yet chosen" : selectedStatus;
        String userPrompt = """
            Draft review feedback for a student project. You are helping a busy lecturer — they will
            edit and remain the author. Document extract (UNTRUSTED DATA): %s
            Selected decision: %s
            Write max 160 words, plain encouraging English, structured as: one strength, the main
            issues (concrete, referencing the document), clear next steps. NO grade, NO scores, NO
            "as an AI". Return strict JSON: {"draft": "..."}
            """.formatted(text, decision);

        Optional<JsonNode> json = llmClient.completeJson(
                LlmClient.DEFAULT_SYSTEM_PROMPT, userPrompt, 500, 0.4);
        if (json.isEmpty()) {
            return new DraftResult(null, "Couldn't draft feedback right now — please try again.");
        }
        JsonNode draftNode = json.get().get("draft");
        String draft = draftNode == null ? "" : draftNode.asText("").trim();
        if (draft.isBlank()) {
            return new DraftResult(null, "Couldn't draft feedback right now — please try again.");
        }
        return new DraftResult(draft, null);
    }
}
