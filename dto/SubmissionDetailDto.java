package com.chuka.irir.dto;

import com.chuka.irir.model.SubmissionType;

import java.time.LocalDateTime;

public record SubmissionDetailDto(
        Long id,
        SubmissionType type,
        String title,
        String description,
        String status,
        String studentName,
        String lecturerName,
        String department,
        String course,
        String unit,
        LocalDateTime submittedAt,
        int fileCount,
        int reviewCount,
        SubmissionInsightDto insights
) {
}
