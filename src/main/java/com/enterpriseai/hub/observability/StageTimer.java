package com.enterpriseai.hub.observability;

/**
 * Minimal stopwatch used to attribute latency to the individual stages of the RAG
 * pipeline (embedding, vector search, generation) without pulling in a metrics façade at
 * every call site. Values end up both in the logs and on the persisted message row.
 */
public final class StageTimer {

    private final long startNanos = System.nanoTime();
    private long lastMark = startNanos;

    public static StageTimer start() {
        return new StageTimer();
    }

    /** Milliseconds elapsed since the previous {@code mark()} (or since creation). */
    public long mark() {
        long now = System.nanoTime();
        long elapsed = (now - lastMark) / 1_000_000;
        lastMark = now;
        return elapsed;
    }

    /** Milliseconds elapsed since the timer was created. */
    public long totalMs() {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
