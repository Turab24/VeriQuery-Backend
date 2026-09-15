package com.enterpriseai.hub.ai;

import java.util.List;

/**
 * Port for turning text into a dense vector.
 *
 * <p>Implementations must be stable: the same text has to map to the same vector across
 * restarts, otherwise previously indexed chunks would stop matching new queries.</p>
 */
public interface EmbeddingClient {

    float[] embed(String text);

    /**
     * Batch variant. Providers charge and rate-limit per request, not per token, so
     * ingestion always goes through this method rather than looping over
     * {@link #embed(String)}.
     */
    List<float[]> embedAll(List<String> texts);

    /** Must match the dimension of the {@code vector(n)} column in PostgreSQL. */
    int dimensions();

    String modelName();
}
