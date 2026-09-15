package com.enterpriseai.hub.dto.search;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "SemanticSearchResults")
public record SearchResultResponse(
        String query,
        int totalResults,
        long embeddingMs,
        long searchMs,
        List<Hit> results) {

    @Schema(name = "SemanticSearchHit")
    public record Hit(
            Long chunkId,
            Long documentId,
            String documentTitle,
            String fileName,
            Integer pageNumber,
            String section,
            String excerpt,
            @Schema(example = "0.78") double similarity) {
    }
}
