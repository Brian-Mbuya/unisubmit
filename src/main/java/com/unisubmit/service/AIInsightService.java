package com.unisubmit.service;

import com.unisubmit.domain.AIInsight;
import com.unisubmit.domain.AIInsightStatus;
import com.unisubmit.domain.Submission;
import com.unisubmit.repository.AIInsightRepository;
import com.unisubmit.repository.SubmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;

/**
 * AI document analysis pipeline — designed to be later replaced
 * with a real AI API call (OpenAI / Gemini) without touching
 * controllers or the UI layer.
 *
 * <p>Pipeline stages:
 * <ol>
 *   <li>Extract raw text with Apache Tika (handles PDF, DOCX, TXT, etc.)</li>
 *   <li>Clean and tokenise the text</li>
 *   <li>Compute term frequencies; pick top 8–12 as keywords</li>
 *   <li>Score sentences by keyword density; pick top 2–3 as extractive summary</li>
 *   <li>Persist results and trigger recommendation computation</li>
 * </ol>
 */
@Service
public class AIInsightService {

    private final AIInsightRepository aiInsightRepository;
    private final SubmissionRepository submissionRepository;
    private final AIInsightProcessingService aiInsightProcessingService;

    public AIInsightService(AIInsightRepository aiInsightRepository,
                            SubmissionRepository submissionRepository,
                            AIInsightProcessingService aiInsightProcessingService) {
        this.aiInsightRepository = aiInsightRepository;
        this.submissionRepository = submissionRepository;
        this.aiInsightProcessingService = aiInsightProcessingService;
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Creates the AIInsight record in PENDING state, then fires the async
     * analysis. Uses TransactionSynchronization to run the async job AFTER
     * the current transaction commits successfully, preventing race conditions.
     */
    @org.springframework.transaction.annotation.Transactional
    public void initiateAnalysis(Submission submission) {
        if (submission == null || submission.getId() == null) {
            return;
        }

        Submission managedSubmission = submissionRepository.findById(submission.getId()).orElse(null);
        if (managedSubmission == null) {
            return;
        }

        AIInsight existingInsight = managedSubmission.getAiInsight();
        if (existingInsight != null) {
            existingInsight.setStatus(AIInsightStatus.PENDING);
            existingInsight.setSummary(null);
            existingInsight.setKeywords(new java.util.LinkedHashSet<>());
            existingInsight.setErrorMessage(null);
            aiInsightRepository.save(existingInsight);

            final Long insightId = existingInsight.getId();
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        aiInsightProcessingService.performAnalysisAsync(insightId);
                    }
                });
            } else {
                aiInsightProcessingService.performAnalysisAsync(insightId);
            }
            return;
        }

        AIInsight insight = new AIInsight();
        insight.setSubmission(managedSubmission);
        insight.setStatus(AIInsightStatus.PENDING);
        insight = aiInsightRepository.save(insight);

        managedSubmission.setAiInsight(insight);
        submissionRepository.save(managedSubmission);

        final Long insightId = insight.getId();
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    aiInsightProcessingService.performAnalysisAsync(insightId);
                }
            });
        } else {
            aiInsightProcessingService.performAnalysisAsync(insightId);
        }
    }

    /**
     * Retry entry point (FAILED analyses only). Atomically flips FAILED → PENDING; if the
     * insight isn't FAILED (already running, already done, or gone) nothing is claimed and
     * this returns false — matching the javadoc's "only allowed when status == FAILED".
     */
    @org.springframework.transaction.annotation.Transactional
    public boolean retryAnalysis(Long insightId) {
        int claimed = aiInsightRepository.transition(insightId, AIInsightStatus.PENDING,
                java.util.List.of(AIInsightStatus.FAILED, AIInsightStatus.DEGRADED));
        if (claimed == 0) {
            return false;
        }
        fireAfterCommit(insightId);
        return true;
    }

    /**
     * Student-triggered re-run from the submission page. Atomically flips a COMPLETED or
     * FAILED insight back to PENDING (a concurrent click, or a run still PROCESSING, is
     * refused) and re-fires the pipeline. Returns false when nothing was claimed.
     */
    @org.springframework.transaction.annotation.Transactional
    public boolean rerunAnalysis(Long submissionId) {
        Submission submission = submissionRepository.findById(submissionId).orElse(null);
        if (submission == null || submission.getAiInsight() == null) {
            return false;
        }
        Long insightId = submission.getAiInsight().getId();
        int claimed = aiInsightRepository.transition(insightId, AIInsightStatus.PENDING,
                java.util.List.of(AIInsightStatus.COMPLETED, AIInsightStatus.FAILED, AIInsightStatus.DEGRADED));
        if (claimed == 0) {
            return false;
        }
        fireAfterCommit(insightId);
        return true;
    }

    /**
     * Runs the async pipeline AFTER the current transaction commits, so the worker thread
     * sees the committed PENDING row (the same guard initiateAnalysis uses).
     */
    private void fireAfterCommit(Long insightId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    aiInsightProcessingService.performAnalysisAsync(insightId);
                }
            });
        } else {
            aiInsightProcessingService.performAnalysisAsync(insightId);
        }
    }
}
