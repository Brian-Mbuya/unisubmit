package com.chuka.irir.service;

import com.chuka.irir.dto.CollaboratorSuggestionDto;
import com.chuka.irir.dto.DocumentAnalysisVersionDto;
import com.chuka.irir.dto.SimilarSubmissionDto;
import com.chuka.irir.dto.SubmissionInsightDto;
import com.chuka.irir.model.AnalysisStatus;
import com.chuka.irir.model.DocumentAnalysis;
import com.chuka.irir.model.Submission;
import com.chuka.irir.model.User;
import com.chuka.irir.repository.DocumentAnalysisRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Transactional(readOnly = true)
public class SimilarityService {

    private final DocumentAnalysisRepository documentAnalysisRepository;
    private final AIAnalysisService aiAnalysisService;
    private final double highSimilarityThreshold;
    private final Map<Long, CachedInsight> insightCache = new ConcurrentHashMap<>();

    public SimilarityService(DocumentAnalysisRepository documentAnalysisRepository,
                             AIAnalysisService aiAnalysisService,
                             @Value("${app.similarity.warning-threshold:0.45}") double highSimilarityThreshold) {
        this.documentAnalysisRepository = documentAnalysisRepository;
        this.aiAnalysisService = aiAnalysisService;
        this.highSimilarityThreshold = highSimilarityThreshold;
    }

    public SubmissionInsightDto buildInsights(Submission submission) {
        if (submission == null || submission.getId() == null) {
            return pendingInsight(AnalysisStatus.PENDING, "Analysis is still running for this submission.");
        }
        DocumentAnalysis latest = aiAnalysisService.getLatestAnalysis(submission.getId());
        CachedInsight cached = insightCache.get(submission.getId());
        if (cached != null && cached.matches(submission, latest)) {
            return cached.insight();
        }
        SubmissionInsightDto insight = buildInsights(submission, latest);
        insightCache.put(submission.getId(), new CachedInsight(
                submission.getId(),
                latest == null ? null : latest.getId(),
                submission.getAnalysisStatus(),
                insight
        ));
        return insight;
    }

    public SubmissionInsightDto buildInsights(Submission submission, DocumentAnalysis latest) {
        if (latest == null) {
            return pendingInsight(submission == null || submission.getAnalysisStatus() == null
                    ? AnalysisStatus.PENDING
                    : submission.getAnalysisStatus(), pendingMessage(submission));
        }

        List<SimilarityMatch> matches = findSimilarityMatches(submission, latest);
        List<SimilarSubmissionDto> similarSubmissions = matches.stream()
                .limit(5)
                .map(match -> new SimilarSubmissionDto(
                        match.submission().getId(),
                        match.submission().getTitle(),
                        match.analysis().getSummary(),
                        match.submission().getUnit() == null ? null : match.submission().getUnit().getName(),
                        match.submission().getLecturer() == null ? null : match.submission().getLecturer().getFullName(),
                        match.score(),
                        match.sharedKeywords()
                ))
                .toList();

        List<CollaboratorSuggestionDto> collaboratorSuggestions = buildCollaboratorSuggestions(submission, matches);
        Double highestScore = matches.isEmpty() ? null : matches.get(0).score();
        boolean highSimilarity = highestScore != null && highestScore >= highSimilarityThreshold;
        String fallback = similarSubmissions.isEmpty()
                ? "No similar submissions found yet. This looks like a fresh topic in the current dataset."
                : null;

        return new SubmissionInsightDto(
                submission.getAnalysisStatus().name(),
                DocumentAnalysisVersionDto.from(latest),
                highestScore,
                highSimilarity,
                similarSubmissions,
                collaboratorSuggestions,
                fallback
        );
    }

    public List<SimilarityMatch> findSimilarityMatches(Submission submission, DocumentAnalysis latestAnalysis) {
        Set<String> sourceKeywords = new LinkedHashSet<>(aiAnalysisService.normalizeKeywords(latestAnalysis.getKeywords()));
        if (sourceKeywords.isEmpty()) {
            return List.of();
        }

        List<DocumentAnalysis> candidates = documentAnalysisRepository.findCandidateAnalyses(
                        submission.getId(),
                        submission.getType(),
                        submission.getUnit() == null ? null : submission.getUnit().getId(),
                        submission.getDepartment() == null ? null : submission.getDepartment().getId()
                );
        if (candidates.isEmpty()) {
            candidates = documentAnalysisRepository.findLatestAnalysesWithSubmissionContext().stream()
                    .filter(candidate -> candidate.getSubmission() != null)
                    .filter(candidate -> !Objects.equals(candidate.getSubmission().getId(), submission.getId()))
                    .filter(candidate -> candidate.getSubmission().getType() == submission.getType())
                    .toList();
        }

        return candidates.stream()
                .map(candidate -> toMatch(candidate, sourceKeywords))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(SimilarityMatch::score).reversed())
                .limit(10)
                .toList();
    }

    private SimilarityMatch toMatch(DocumentAnalysis candidate, Set<String> sourceKeywords) {
        Set<String> candidateKeywords = new LinkedHashSet<>(aiAnalysisService.normalizeKeywords(candidate.getKeywords()));
        if (candidateKeywords.isEmpty()) {
            return null;
        }

        List<String> shared = sourceKeywords.stream()
                .filter(candidateKeywords::contains)
                .sorted()
                .toList();
        if (shared.isEmpty()) {
            return null;
        }

        Set<String> total = new LinkedHashSet<>(sourceKeywords);
        total.addAll(candidateKeywords);
        double score = total.isEmpty() ? 0.0 : (double) shared.size() / total.size();
        return new SimilarityMatch(candidate.getSubmission(), candidate, round(score), shared);
    }

    private List<CollaboratorSuggestionDto> buildCollaboratorSuggestions(Submission source, List<SimilarityMatch> matches) {
        Map<Long, CollaboratorSuggestionDto> suggestions = new LinkedHashMap<>();

        for (SimilarityMatch match : matches.stream().limit(5).toList()) {
            Submission candidate = match.submission();
            User student = candidate.getStudent();
            if (student != null && !Objects.equals(student.getId(), source.getStudent().getId())) {
                suggestions.putIfAbsent(student.getId(), new CollaboratorSuggestionDto(
                        "STUDENT",
                        student.getId(),
                        student.getFullName(),
                        "Student working on related themes",
                        match.sharedKeywords()
                ));
            }

            User lecturer = candidate.getLecturer();
            if (lecturer != null && !Objects.equals(lecturer.getId(), source.getLecturer().getId())) {
                String context = candidate.getUnit() == null
                        ? "Lecturer reviewing similar work"
                        : "Lecturer assigned to similar unit: " + candidate.getUnit().getName();
                suggestions.putIfAbsent(lecturer.getId(), new CollaboratorSuggestionDto(
                        "LECTURER",
                        lecturer.getId(),
                        lecturer.getFullName(),
                        context,
                        match.sharedKeywords()
                ));
            }
        }

        return new ArrayList<>(suggestions.values());
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public void invalidateCache() {
        insightCache.clear();
    }

    private SubmissionInsightDto pendingInsight(AnalysisStatus status, String message) {
        return new SubmissionInsightDto(
                status.name(),
                null,
                null,
                false,
                List.of(),
                List.of(),
                message
        );
    }

    private String pendingMessage(Submission submission) {
        if (submission == null || submission.getAnalysisStatus() == null) {
            return "Analysis is still running for this submission.";
        }
        return switch (submission.getAnalysisStatus()) {
            case FAILED -> "Analysis failed and will be retried if attempts remain.";
            case ANALYZING -> "Analysis is currently running for this submission.";
            case COMPLETED -> "Analysis data is temporarily unavailable.";
            case PENDING -> "Analysis is queued for this submission.";
        };
    }

    private record CachedInsight(
            Long submissionId,
            Long latestAnalysisId,
            AnalysisStatus analysisStatus,
            SubmissionInsightDto insight
    ) {
        private boolean matches(Submission submission, DocumentAnalysis latestAnalysis) {
            return Objects.equals(submissionId, submission.getId())
                    && Objects.equals(latestAnalysisId, latestAnalysis == null ? null : latestAnalysis.getId())
                    && Objects.equals(analysisStatus, submission.getAnalysisStatus());
        }
    }

    public record SimilarityMatch(
            Submission submission,
            DocumentAnalysis analysis,
            Double score,
            List<String> sharedKeywords
    ) {
    }
}
