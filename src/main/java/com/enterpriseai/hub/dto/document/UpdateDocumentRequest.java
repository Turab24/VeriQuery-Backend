package com.enterpriseai.hub.dto.document;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "UpdateDocumentRequest")
public record UpdateDocumentRequest(

        @NotBlank(message = "Title is required")
        @Size(max = 255)
        String title,

        @Schema(example = "BANKING", allowableValues = {"TECHNICAL", "BUSINESS", "POLICY", "BANKING", "API_DOCUMENTATION", "OTHER"})
        String category) {
}
