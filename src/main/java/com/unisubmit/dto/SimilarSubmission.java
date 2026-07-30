package com.unisubmit.dto;

import com.unisubmit.domain.Submission;

import java.util.List;

/**
 * Immutable result produced by RecommendationService for a single
 * candidate submission that shares topic overlap with the current one.
 * Carries the full per-signal breakdown so the UI can explain
 * "why this match" instead of only showing a final label.
 *
 * @param submission          the candidate submission
 * @param matchLabel          human-readable match strength label
 * @param sharedKeywords      free-text keywords found in both submissions
 * @param sharedTechnologies  structured Technology tags shared by both
 * @param sharedResearchAreas structured ResearchArea tags shared by both
 * @param scoreNormalized     0.0–1.0 normalised overall score
 * @param reason              short computed explanation string
 * @param keywordScore        0..1 keyword-overlap signal
 * @param titleScore          0..1 title word-overlap signal
 * @param unitScore           0..1 same-unit/same-department signal
 * @param semanticScore       0..1 embedding cosine similarity signal
 * @param technologyScore     0..1 structured technology-overlap signal
 * @param researchAreaScore   0..1 structured research-area-overlap signal
 * @param sameUnit            whether both submissions belong to the same unit
 */
public record SimilarSubmission(
        Submission submission,
        String matchLabel,
        List<String> sharedKeywords,
        List<String> sharedTechnologies,
        List<String> sharedResearchAreas,
        double scoreNormalized,
        String reason,
        double keywordScore,
        double titleScore,
        double unitScore,
        double semanticScore,
        double technologyScore,
        double researchAreaScore,
        boolean sameUnit
) {}
