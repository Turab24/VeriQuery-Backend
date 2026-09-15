package com.enterpriseai.hub.dto.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "AskDocumentRequest", description = "Ask a question scoped to a single document")
public record AskDocumentRequest(

        @NotBlank(message = "A question is required")
        @Size(max = 4000)
        String question) {
}
