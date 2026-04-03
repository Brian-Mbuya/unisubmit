package com.chuka.irir.dto;

import com.chuka.irir.model.DocumentAnalysis;

import java.time.LocalDateTime;
import java.util.List;

public record DocumentAnalysisVersionDto(
        Integer version,
        String summary,
        List<String> keywords,
        List<String> topics,
        LocalDateTime createdAt
) {
    public static DocumentAnalysisVersionDto from(DocumentAnalysis analysis) {
        List<String> keywords = analysis.getKeywords() == null ? List.of() : List.copyOf(analysis.getKeywords());
        List<String> topics = analysis.getTopics() == null ? List.of() : List.copyOf(analysis.getTopics());
        return new DocumentAnalysisVersionDto(
                analysis.getId() == null ? null : Math.toIntExact(analysis.getId()),
                analysis.getSummary(),
                keywords,
                topics,
                analysis.getCreatedAt()
        );
    }
}
