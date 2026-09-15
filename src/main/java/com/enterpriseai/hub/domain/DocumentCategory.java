package com.enterpriseai.hub.domain;

import java.util.Arrays;
import java.util.Locale;

/**
 * Content classification assigned by the LLM after ingestion.
 */
public enum DocumentCategory {

    TECHNICAL,
    BUSINESS,
    POLICY,
    BANKING,
    API_DOCUMENTATION,
    OTHER;

    /**
     * Lenient parser: model output is free text, so anything unrecognised falls back to
     * {@link #OTHER} rather than failing the ingestion pipeline.
     */
    public static DocumentCategory fromModelOutput(String raw) {
        if (raw == null || raw.isBlank()) {
            return OTHER;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        return Arrays.stream(values())
                .filter(category -> normalized.contains(category.name()))
                .findFirst()
                .orElse(OTHER);
    }
}
