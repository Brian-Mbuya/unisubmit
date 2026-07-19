package com.unisubmit.service.ai;

import com.unisubmit.domain.AIInsight;
import com.unisubmit.domain.AIInsightStatus;
import com.unisubmit.domain.Submission;
import com.unisubmit.repository.SubmissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

/**
 * One-shot admin job that fills in missing document embeddings for COMPLETED insights, so a
 * corpus analysed before the embeddings key was configured becomes semantically searchable.
 * <p>
 * Deliberately EXCLUDES DEGRADED insights — their heuristic summary would pollute the vector
 * space (see AIInsightStatus.hasContent, which the backfill does NOT use). Idempotent: a
 * re-run skips any submission that already has an embedding.
 */
@Service
public class EmbeddingBackfillService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingBackfillService.class);
    private static final int BATCH_SIZE = 20;

    private final SubmissionRepository submissionRepository;
    private final LlmClient llmClient;
    private final TransactionTemplate transactionTemplate;

    public EmbeddingBackfillService(SubmissionRepository submissionRepository,
                                    LlmClient llmClient,
                                    PlatformTransactionManager transactionManager) {
        this.submissionRepository = submissionRepository;
        this.llmClient = llmClient;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Async
    public void backfill() {
        if (!llmClient.hasEmbeddingsKey()) {
            log.warn("Embedding backfill requested but no embeddings key is configured — nothing to do.");
            return;
        }

        List<Long> ids = submissionRepository.findIdsByInsightStatus(AIInsightStatus.COMPLETED);
        log.info("Embedding backfill: {} COMPLETED submissions to consider.", ids.size());

        int embedded = 0;
        int skipped = 0;
        for (int from = 0; from < ids.size(); from += BATCH_SIZE) {
            List<Long> batch = ids.subList(from, Math.min(from + BATCH_SIZE, ids.size()));
            int[] counts = transactionTemplate.execute(status -> processBatch(batch));
            embedded += counts[0];
            skipped += counts[1];
            log.info("Embedding backfill: batch done — {} embedded, {} skipped ({} of {} scanned).",
                    counts[0], counts[1], Math.min(from + BATCH_SIZE, ids.size()), ids.size());
        }
        log.info("Embedding backfill complete: {} embedded, {} skipped.", embedded, skipped);
    }

    /** Returns [embedded, skipped] for the batch. */
    private int[] processBatch(List<Long> batch) {
        int embedded = 0;
        int skipped = 0;
        for (Long id : batch) {
            Submission submission = submissionRepository.findById(id).orElse(null);
            if (submission == null || submission.getEmbedding() != null) {
                skipped++; // idempotent — already embedded, or gone
                continue;
            }
            AIInsight insight = submission.getAiInsight();
            if (insight == null || insight.getStatus() != AIInsightStatus.COMPLETED) {
                skipped++;
                continue;
            }
            String keywords = insight.getKeywords() != null ? String.join(" ", insight.getKeywords()) : "";
            String text = (submission.getTitle() == null ? "" : submission.getTitle())
                    + " " + (insight.getSummary() == null ? "" : insight.getSummary())
                    + " " + keywords;
            Optional<float[]> embeddingOpt = llmClient.embed(text.trim());
            if (embeddingOpt.isPresent()) {
                submission.setEmbedding(embeddingOpt.get());
                submissionRepository.save(submission);
                embedded++;
            } else {
                skipped++;
            }
            // Gentle pacing so a large corpus doesn't hammer the embeddings provider.
            try {
                Thread.sleep(100);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return new int[]{embedded, skipped};
    }
}
