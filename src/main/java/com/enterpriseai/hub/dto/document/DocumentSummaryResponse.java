package com.enterpriseai.hub.dto.document;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "DocumentSummary")
public record DocumentSummaryResponse(
        Long documentId,
        String title,
        String summary,
        @Schema(description = "True when the summary was served from the stored value rather than regenerated")
        boolean cached,
        String model,
        long generationMs) {
}
