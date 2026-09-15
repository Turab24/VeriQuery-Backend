package com.enterpriseai.hub.dto.conversation;

import com.enterpriseai.hub.dto.chat.CitationResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(name = "Message")
public record MessageResponse(
        Long id,
        @Schema(allowableValues = {"USER", "ASSISTANT", "SYSTEM"}) String role,
        String content,
        List<CitationResponse> citations,
        String model,
        Long retrievalMs,
        Long llmMs,
        Long totalMs,
        Double topSimilarity,
        Instant createdAt) {
}
