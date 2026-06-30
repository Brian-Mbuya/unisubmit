package com.unisubmit.dto;

import com.unisubmit.domain.Submission;

import java.util.List;

/**
 * Immutable result produced by RecommendationService for a single
 * candidate submission that shares topic overlap with the current one.
 *
 * @param submission    the candidate submission
 * @param matchLabel    human-readable match strength label
 *                      (e.g. "🔥 Strong Match", "🧠 Related", "📘 Same Unit")
 * @param sharedKeywords keywords found in both submissions
 * @param scoreNormalized 0.0–1.0 normalised overlap score
 */
public record SimilarSubmission(
        Submission submission,
        String matchLabel,
        List<String> sharedKeywords,
        double scoreNormalized,
        String reason
) {}
