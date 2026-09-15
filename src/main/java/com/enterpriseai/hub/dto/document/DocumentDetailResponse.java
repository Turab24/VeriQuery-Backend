package com.enterpriseai.hub.dto.document;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "DocumentDetail", description = "Document metadata plus a preview of its indexed chunks")
public record DocumentDetailResponse(
        DocumentResponse document,
        String summary,
        List<ChunkPreview> chunkPreviews) {

    @Schema(name = "ChunkPreview")
    public record ChunkPreview(
            Long id,
            int chunkIndex,
            Integer pageNumber,
            String section,
            int characterCount,
            String excerpt) {
    }
}
