package com.enterpriseai.hub.dto.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(name = "ChatRequest", description = "A question to answer against the knowledge base")
public record ChatRequest(

        @Schema(example = "What is the process for requesting a cheque book?")
        @NotBlank(message = "A question is required")
        @Size(max = 4000, message = "Questions are limited to 4000 characters")
        String question,

        @Schema(description = "Existing conversation to append to. Omit to start a new conversation.")
        Long conversationId,

        @Schema(description = "Restrict retrieval to these document ids. Omit to search the whole knowledge base.")
        List<Long> documentIds) {
}
