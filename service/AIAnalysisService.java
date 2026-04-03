package com.chuka.irir.service;

import com.chuka.irir.model.DocumentAnalysis;
import com.chuka.irir.model.AnalysisStatus;
import com.chuka.irir.model.Submission;
import com.chuka.irir.model.SubmissionFile;
import com.chuka.irir.repository.DocumentAnalysisRepository;
import com.chuka.irir.repository.SubmissionRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.time.LocalDateTime;

@Service
public class AIAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(AIAnalysisService.class);
    private static final int MAX_ANALYSIS_ATTEMPTS = 3;

    private static final Pattern TOKEN_SPLITTER = Pattern.compile("[^a-zA-Z0-9]+");
    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "been", "being", "by", "for", "from", "has", "have", "had",
            "in", "into", "is", "it", "its", "of", "on", "or", "that", "the", "their", "this", "to", "was", "were",
            "will", "with", "using", "used", "use", "than", "then", "them", "they", "your", "our", "can", "may",
            "also", "such", "within", "through", "across", "over", "under", "about", "between",
            "project", "submission", "coursework", "final", "year", "study", "student", "lecturer"
    );

    private final SubmissionRepository submissionRepository;
    private final DocumentAnalysisRepository documentAnalysisRepository;
    private final FileStorageService fileStorageService;
    private final ObjectProvider<AIAnalysisService> selfProvider;
    private final ObjectProvider<SimilarityService> similarityServiceProvider;

    public AIAnalysisService(SubmissionRepository submissionRepository,
                             DocumentAnalysisRepository documentAnalysisRepository,
                             FileStorageService fileStorageService,
                             ObjectProvider<AIAnalysisService> selfProvider,
                             ObjectProvider<SimilarityService> similarityServiceProvider) {
        this.submissionRepository = submissionRepository;
        this.documentAnalysisRepository = documentAnalysisRepository;
        this.fileStorageService = fileStorageService;
        this.selfProvider = selfProvider;
        this.similarityServiceProvider = similarityServiceProvider;
    }

    @Async("analysisTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void analyzeSubmissionAsync(Long submissionId) {
        try {
            analyzeSubmission(submissionId);
        } catch (Exception ex) {
            logger.error("Asynchronous document analysis failed for submission {}", submissionId, ex);
            scheduleRetryIfPossible(submissionId, ex);
        }
    }

    public void enqueueSubmissionAnalysis(Long submissionId) {
        if (submissionId == null) {
            return;
        }
        markPending(submissionId);
        AIAnalysisService self = selfProvider.getObject();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    self.analyzeSubmissionAsync(submissionId);
                }
            });
            return;
        }
        self.analyzeSubmissionAsync(submissionId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DocumentAnalysis analyzeSubmission(Long submissionId) {
        Submission submission = markAnalyzing(submissionId);
        if (submission == null) {
            return null;
        }

        Submission sourceSubmission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("Submission not found: " + submissionId));

        String sourceText = extractSubmissionText(sourceSubmission);
        String combinedContext = StreamBuilder.build(
                sourceSubmission.getTitle(),
                sourceSubmission.getDescription(),
                sourceText
        );
        List<String> keywords = extractKeywords(combinedContext);
        List<String> topics = extractTopics(keywords);
        String summary = summarize(combinedContext, keywords);

        DocumentAnalysis analysis = DocumentAnalysis.builder()
                .submission(sourceSubmission)
                .summary(summary)
                .keywords(keywords)
                .topics(topics)
                .build();

        DocumentAnalysis saved = documentAnalysisRepository.save(analysis);
        markCompleted(submissionId);
        similarityServiceProvider.ifAvailable(SimilarityService::invalidateCache);
        return saved;
    }

    @Transactional(readOnly = true)
    public DocumentAnalysis getLatestAnalysis(Long submissionId) {
        return documentAnalysisRepository.findFirstBySubmissionIdOrderByCreatedAtDescIdDesc(submissionId).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<DocumentAnalysis> getAnalysisHistory(Long submissionId) {
        return documentAnalysisRepository.findBySubmissionIdOrderByCreatedAtDescIdDesc(submissionId);
    }

    public List<String> normalizeKeywords(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return List.of();
        }
        return keywords.stream()
                .filter(Objects::nonNull)
                .map(value -> value.trim().toLowerCase(Locale.ENGLISH))
                .filter(value -> value.length() >= 3)
                .filter(value -> !STOPWORDS.contains(value))
                .distinct()
                .toList();
    }

    private String extractSubmissionText(Submission submission) {
        List<String> segments = new ArrayList<>();
        if (submission.getFiles() != null) {
            for (SubmissionFile file : submission.getFiles()) {
                String text = fileStorageService.extractTextFromStoredFile(file.getStoragePath());
                if (text != null && !text.isBlank()) {
                    segments.add(text);
                }
            }
        }
        return String.join("\n\n", segments);
    }

    private List<String> extractKeywords(String text) {
        String normalized = normalizeInput(text);
        if (normalized.isBlank()) {
            return List.of();
        }

        Map<String, Integer> frequency = new LinkedHashMap<>();
        Arrays.stream(TOKEN_SPLITTER.split(normalized))
                .map(token -> token.toLowerCase(Locale.ENGLISH).trim())
                .filter(token -> token.length() >= 4)
                .filter(token -> !STOPWORDS.contains(token))
                .forEach(token -> frequency.merge(token, 1, Integer::sum));

        return frequency.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry::getKey))
                .limit(8)
                .map(Map.Entry::getKey)
                .toList();
    }

    private List<String> extractTopics(List<String> keywords) {
        return keywords.stream()
                .limit(4)
                .map(this::titleCase)
                .toList();
    }

    private String summarize(String text, List<String> keywords) {
        String normalized = normalizeInput(text);
        if (normalized.isBlank()) {
            if (keywords.isEmpty()) {
                return "Analysis is pending enough document text to generate a summary.";
            }
            return "This submission focuses on " + String.join(", ", keywords) + ".";
        }

        String[] sentences = normalized.split("(?<=[.!?])\\s+");
        List<String> selected = Arrays.stream(sentences)
                .map(String::trim)
                .filter(sentence -> sentence.length() > 30)
                .limit(2)
                .collect(Collectors.toCollection(ArrayList::new));

        if (selected.isEmpty()) {
            String[] words = normalized.split("\\s+");
            selected.add(Arrays.stream(words).limit(40).collect(Collectors.joining(" ")).trim());
        }

        String summary = String.join(" ", selected).trim();
        if (summary.isBlank()) {
            summary = "This submission was received and is being processed for academic review.";
        }

        if (!keywords.isEmpty() && !containsAny(summary, keywords)) {
            summary = summary + " Key themes include " + String.join(", ", keywords.subList(0, Math.min(3, keywords.size()))) + ".";
        }
        return summary;
    }

    private boolean containsAny(String text, List<String> keywords) {
        String lower = text.toLowerCase(Locale.ENGLISH);
        return keywords.stream().anyMatch(lower::contains);
    }

    private String normalizeInput(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private String titleCase(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value.substring(0, 1).toUpperCase(Locale.ENGLISH) + value.substring(1);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void markPending(Long submissionId) {
        submissionRepository.findById(submissionId).ifPresent(submission -> {
            if (submission.getAnalysisStatus() != AnalysisStatus.ANALYZING) {
                submission.setAnalysisStatus(AnalysisStatus.PENDING);
            }
            submission.setAnalysisRequestedAt(LocalDateTime.now());
            submission.setAnalysisCompletedAt(null);
            submission.setLastAnalysisError(null);
            submissionRepository.save(submission);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected Submission markAnalyzing(Long submissionId) {
        Submission submission = submissionRepository.findWithLockById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("Submission not found: " + submissionId));
        if (submission.getAnalysisStatus() == AnalysisStatus.ANALYZING) {
            return null;
        }
        if (submission.getAnalysisAttempts() != null && submission.getAnalysisAttempts() >= MAX_ANALYSIS_ATTEMPTS
                && submission.getAnalysisStatus() == AnalysisStatus.FAILED) {
            return null;
        }
        submission.setAnalysisStatus(AnalysisStatus.ANALYZING);
        submission.setAnalysisAttempts((submission.getAnalysisAttempts() == null ? 0 : submission.getAnalysisAttempts()) + 1);
        submission.setLastAnalysisError(null);
        submission.setAnalysisRequestedAt(LocalDateTime.now());
        submissionRepository.save(submission);
        return submission;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void markCompleted(Long submissionId) {
        submissionRepository.findById(submissionId).ifPresent(submission -> {
            submission.setAnalysisStatus(AnalysisStatus.COMPLETED);
            submission.setAnalysisCompletedAt(LocalDateTime.now());
            submission.setLastAnalysisError(null);
            submissionRepository.save(submission);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected boolean markFailed(Long submissionId, Exception ex) {
        return submissionRepository.findById(submissionId)
                .map(submission -> {
                    submission.setAnalysisStatus(AnalysisStatus.FAILED);
                    submission.setAnalysisCompletedAt(LocalDateTime.now());
                    submission.setLastAnalysisError(ex == null ? "Unknown analysis failure." : ex.getMessage());
                    submissionRepository.save(submission);
                    Integer attempts = submission.getAnalysisAttempts() == null ? 0 : submission.getAnalysisAttempts();
                    return attempts < MAX_ANALYSIS_ATTEMPTS;
                })
                .orElse(false);
    }

    private void scheduleRetryIfPossible(Long submissionId, Exception ex) {
        if (!markFailed(submissionId, ex)) {
            return;
        }
        enqueueSubmissionAnalysis(submissionId);
    }

    private static final class StreamBuilder {
        private StreamBuilder() {
        }

        static String build(String... values) {
            return Arrays.stream(values)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .collect(Collectors.joining("\n\n"));
        }
    }
}
