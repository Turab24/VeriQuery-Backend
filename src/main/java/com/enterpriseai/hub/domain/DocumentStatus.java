package com.enterpriseai.hub.domain;

/**
 * Lifecycle of a document inside the ingestion pipeline.
 *
 * <pre>
 * PENDING --&gt; PROCESSING --&gt; READY
 *                   \--&gt; FAILED
 * </pre>
 */
public enum DocumentStatus {

    /** Stored on disk, metadata persisted, waiting for the async worker. */
    PENDING,
    /** Text extraction, chunking and embedding are in progress. */
    PROCESSING,
    /** Chunks and embeddings are queryable. */
    READY,
    /** Processing failed; {@code processingError} explains why. */
    FAILED
}
