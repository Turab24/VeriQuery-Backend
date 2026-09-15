package com.enterpriseai.hub.rag;

/**
 * A passage returned by the vector store together with everything needed to cite it.
 *
 * @param similarity cosine similarity in [0,1] (1 = identical direction), computed as
 *                   {@code 1 - cosine_distance} in SQL.
 */
public record RetrievedChunk(
        Long chunkId,
        Long documentId,
        String documentTitle,
        String fileName,
        int chunkIndex,
        Integer pageNumber,
        String section,
        String content,
        double similarity) {

    public String citationLabel() {
        StringBuilder label = new StringBuilder(documentTitle);
        if (pageNumber != null) {
            label.append(", page ").append(pageNumber);
        }
        if (section != null && !section.isBlank()) {
            label.append(" - ").append(section);
        }
        return label.toString();
    }

    public String excerpt(int maxCharacters) {
        String normalized = content.replaceAll("\\s+", " ").strip();
        return normalized.length() <= maxCharacters
                ? normalized
                : normalized.substring(0, maxCharacters).strip() + "...";
    }
}
