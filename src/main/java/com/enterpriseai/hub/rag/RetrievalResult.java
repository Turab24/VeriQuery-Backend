package com.enterpriseai.hub.rag;

import java.util.List;

/**
 * Output of the retrieval stage, including the timings that make the pipeline debuggable.
 */
public record RetrievalResult(
        List<RetrievedChunk> chunks,
        long embeddingMs,
        long searchMs,
        int candidatesConsidered) {

    public boolean isEmpty() {
        return chunks.isEmpty();
    }

    public double topSimilarity() {
        return chunks.isEmpty() ? 0.0 : chunks.get(0).similarity();
    }

    /**
     * A coarse, honest label for how well the knowledge base covered the question.
     *
     * <p>It is derived purely from retrieval similarity. It says something about whether
     * relevant material was found - it does <b>not</b> say the generated answer is correct,
     * and it is never presented to the user as a correctness score.</p>
     */
    public String confidenceLabel() {
        if (chunks.isEmpty()) {
            return "NONE";
        }
        double top = topSimilarity();
        if (top >= 0.60) {
            return "HIGH";
        }
        if (top >= 0.35) {
            return "MEDIUM";
        }
        return "LOW";
    }

    public static RetrievalResult empty(long embeddingMs, long searchMs, int candidates) {
        return new RetrievalResult(List.of(), embeddingMs, searchMs, candidates);
    }
}
