package com.unisubmit.dto;

import java.util.List;

/**
 * Represents a suggested collaborator — a real student whose submissions
 * share significant keyword overlap with the current user's work.
 *
 * @param studentName      full name of the suggested collaborator
 * @param studentId        their academic student ID
 * @param sharedKeywords   keywords common across all their overlapping submissions
 * @param matchStrength    total shared keyword count (higher = stronger suggestion)
 */
public record CollaboratorDTO(
        String studentName,
        String studentId,
        List<String> sharedKeywords,
        int matchStrength
) {}
