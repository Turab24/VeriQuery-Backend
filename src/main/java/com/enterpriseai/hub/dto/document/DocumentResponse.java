package com.enterpriseai.hub.dto.document;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "Document", description = "Knowledge base document metadata")
public record DocumentResponse(
        Long id,
        String title,
        String fileName,
        String contentType,
        long fileSize,
        @Schema(example = "READY", allowableValues = {"PENDING", "PROCESSING", "READY", "FAILED"}) String status,
        @Schema(example = "BANKING") String category,
        Integer pageCount,
        int chunkCount,
        int characterCount,
        String processingError,
        Long processingMs,
        String uploadedByName,
        Long uploadedById,
        Instant createdAt,
        Instant processedAt) {
}
