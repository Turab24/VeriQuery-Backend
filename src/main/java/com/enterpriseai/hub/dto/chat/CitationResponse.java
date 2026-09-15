package com.enterpriseai.hub.dto.chat;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "Citation", description = "A passage that was supplied to the model as context")
public record CitationResponse(
        @Schema(description = "1-based position in the context window, matching the [1] markers in the answer")
        int rank,
        Long documentId,
        String documentTitle,
        String fileName,
        Long chunkId,
        Integer pageNumber,
        String section,
        String excerpt,
        @Schema(description = "Cosine similarity between the question and the passage, in [0,1]", example = "0.83")
        double similarity) {
}
