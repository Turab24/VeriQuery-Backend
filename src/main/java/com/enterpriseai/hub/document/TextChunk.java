package com.enterpriseai.hub.document;

public record TextChunk(
        int index,
        String content,
        Integer pageNumber,
        String section,
        int characterCount,
        int tokenEstimate) {
}
