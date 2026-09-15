package com.enterpriseai.hub.dto.document;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "DocumentFaq", description = "Question/answer pairs generated from a document")
public record DocumentFaqResponse(
        Long documentId,
        String title,
        List<FaqItem> items,
        String model,
        long generationMs) {

    @Schema(name = "FaqItem")
    public record FaqItem(String question, String answer) {
    }
}
