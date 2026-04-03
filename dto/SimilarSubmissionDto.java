package com.chuka.irir.dto;

import java.util.List;

public record SimilarSubmissionDto(
        Long submissionId,
        String title,
        String summary,
        String unit,
        String lecturerName,
        Double similarityScore,
        List<String> basedOnKeywords
) {
}
