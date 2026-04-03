package com.chuka.irir.dto;

import java.util.List;

public record CollaboratorSuggestionDto(
        String type,
        Long userId,
        String name,
        String context,
        List<String> basedOnKeywords
) {
}
