package com.enterpriseai.hub.mapper;

import com.enterpriseai.hub.domain.Document;
import com.enterpriseai.hub.domain.DocumentChunk;
import com.enterpriseai.hub.dto.document.DocumentDetailResponse;
import com.enterpriseai.hub.dto.document.DocumentResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DocumentMapper {

    private static final int PREVIEW_LENGTH = 320;

    public DocumentResponse toResponse(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getTitle(),
                document.getFileName(),
                document.getContentType(),
                document.getFileSize(),
                document.getStatus().name(),
                document.getCategory() == null ? null : document.getCategory().name(),
                document.getPageCount(),
                document.getChunkCount(),
                document.getCharacterCount(),
                document.getProcessingError(),
                document.getProcessingMs(),
                document.getUploadedBy() == null ? null : document.getUploadedBy().getFullName(),
                document.getUploadedBy() == null ? null : document.getUploadedBy().getId(),
                document.getCreatedAt(),
                document.getProcessedAt());
    }

    public DocumentDetailResponse toDetail(Document document, List<DocumentChunk> chunks) {
        List<DocumentDetailResponse.ChunkPreview> previews = chunks.stream()
                .map(chunk -> new DocumentDetailResponse.ChunkPreview(
                        chunk.getId(),
                        chunk.getChunkIndex(),
                        chunk.getPageNumber(),
                        chunk.getSection(),
                        chunk.getCharacterCount(),
                        excerpt(chunk.getContent())))
                .toList();
        return new DocumentDetailResponse(toResponse(document), document.getSummary(), previews);
    }

    private String excerpt(String content) {
        String normalized = content.replaceAll("\\s+", " ").strip();
        return normalized.length() <= PREVIEW_LENGTH
                ? normalized
                : normalized.substring(0, PREVIEW_LENGTH) + "...";
    }
}
