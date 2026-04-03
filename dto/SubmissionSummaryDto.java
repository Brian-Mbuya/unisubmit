package com.chuka.irir.dto;

import com.chuka.irir.model.Submission;
import com.chuka.irir.model.SubmissionType;

import java.time.LocalDateTime;
import java.util.List;

public record SubmissionSummaryDto(
        Long id,
        SubmissionType type,
        String title,
        String status,
        String studentName,
        String lecturerName,
        String department,
        String course,
        String unit,
        Long finalYearProjectId,
        LocalDateTime submittedAt,
        int fileCount,
        int reviewCount,
        String analysisStatus,
        Integer analysisVersion,
        String aiSummary,
        List<String> aiKeywords,
        Double highestSimilarityScore,
        boolean highSimilarityWarning,
        List<String> similarityReasons
) {
    public static SubmissionSummaryDto from(Submission submission,
                                            String analysisStatus,
                                            Integer analysisVersion,
                                            String aiSummary,
                                            List<String> aiKeywords,
                                            Double highestSimilarityScore,
                                            boolean highSimilarityWarning,
                                            List<String> similarityReasons,
                                            int fileCount,
                                            int reviewCount) {
        return new SubmissionSummaryDto(
                submission.getId(),
                submission.getType(),
                submission.getTitle(),
                submission.getStatus().name(),
                submission.getStudent() == null ? null : submission.getStudent().getFullName(),
                submission.getLecturer() == null ? null : submission.getLecturer().getFullName(),
                submission.getDepartment() == null ? null : submission.getDepartment().getName(),
                submission.getCourse() == null ? null : submission.getCourse().getName(),
                submission.getUnit() == null ? null : submission.getUnit().getName(),
                submission.getFinalYearProject() == null ? null : submission.getFinalYearProject().getId(),
                submission.getSubmittedAt(),
                fileCount,
                reviewCount,
                analysisStatus,
                analysisVersion,
                aiSummary,
                aiKeywords,
                highestSimilarityScore,
                highSimilarityWarning,
                similarityReasons
        );
    }
}
