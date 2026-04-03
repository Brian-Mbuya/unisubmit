package com.chuka.irir.dto;

import java.util.List;

public record SubmissionInsightDto(
        String analysisStatus,
        DocumentAnalysisVersionDto latestAnalysis,
        Double highestSimilarityScore,
        boolean highSimilarityWarning,
        List<SimilarSubmissionDto> similarSubmissions,
        List<CollaboratorSuggestionDto> collaboratorSuggestions,
        String fallbackMessage
) {
}
