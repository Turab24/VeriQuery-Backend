package com.enterpriseai.hub.dto.chat;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(name = "ChatResponse", description = "Grounded answer plus its provenance and timings")
public record ChatResponse(
        Long conversationId,
        String conversationTitle,
        Long messageId,
        String answer,
        List<CitationResponse> citations,
        @Schema(description = "How well the retrieved context covered the question. "
                + "Derived from retrieval similarity only - it is not a claim about factual correctness.",
                allowableValues = {"HIGH", "MEDIUM", "LOW", "NONE"})
        String retrievalConfidence,
        double topSimilarity,
        int retrievedChunks,
        String model,
        Timings timings,
        Instant createdAt) {

    @Schema(name = "ChatTimings", description = "Per-stage latency in milliseconds")
    public record Timings(long embeddingMs, long retrievalMs, long llmMs, long totalMs) {
    }
}
