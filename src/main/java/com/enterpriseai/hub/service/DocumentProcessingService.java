package com.enterpriseai.hub.service;

import com.enterpriseai.hub.ai.EmbeddingClient;
import com.enterpriseai.hub.config.AppProperties;
import com.enterpriseai.hub.config.AsyncConfig;
import com.enterpriseai.hub.document.ExtractedText;
import com.enterpriseai.hub.document.TextChunk;
import com.enterpriseai.hub.document.TextChunker;
import com.enterpriseai.hub.document.TextExtractionService;
import com.enterpriseai.hub.domain.Document;
import com.enterpriseai.hub.domain.DocumentCategory;
import com.enterpriseai.hub.domain.DocumentStatus;
import com.enterpriseai.hub.exception.ApiException;
import com.enterpriseai.hub.observability.StageTimer;
import com.enterpriseai.hub.repository.DocumentRepository;
import com.enterpriseai.hub.repository.VectorSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The ingestion pipeline.
 *
 * <pre>
 * stored file -&gt; extract text -&gt; chunk -&gt; embed (batched) -&gt; store vectors
 *                                              -&gt; classify + summarise -&gt; READY
 * </pre>
 *
 * <p>Transactions are opened explicitly around the short database steps with a
 * {@link TransactionTemplate} instead of annotating the whole method. Embedding a large
 * document takes seconds to minutes; holding a database connection and an open transaction
 * for that entire time would exhaust the pool under any real load and turn a slow upstream
 * into a database outage.</p>
 *
 * <p>Failures are terminal but recorded: the document moves to {@code FAILED} with the
 * reason, stays visible in the UI, and can be retried from the document detail page.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentProcessingService {

    private final DocumentRepository documentRepository;
    private final VectorSearchRepository vectorSearchRepository;
    private final TextExtractionService textExtractionService;
    private final TextChunker textChunker;
    private final EmbeddingClient embeddingClient;
    private final DocumentAiService documentAiService;
    private final StorageService storageService;
    private final TransactionTemplate transactionTemplate;
    private final AppProperties properties;

    @Async(AsyncConfig.DOCUMENT_EXECUTOR)
    public void processAsync(Long documentId) {
        process(documentId);
    }

    /**
     * Synchronous entry point - used by the async wrapper, by the retry endpoint and by
     * integration tests that need deterministic completion.
     */
    public void process(Long documentId) {
        StageTimer timer = StageTimer.start();
        log.info("Ingestion started for document {}", documentId);

        DocumentSnapshot snapshot = transactionTemplate.execute(status -> {
            Document document = documentRepository.findById(documentId).orElse(null);
            if (document == null) {
                return null;
            }
            document.markProcessing();
            documentRepository.save(document);
            return new DocumentSnapshot(document.getId(), document.getStoragePath(),
                    document.getContentType(), document.getFileName(), document.getTitle());
        });

        if (snapshot == null) {
            log.warn("Ingestion skipped: document {} no longer exists", documentId);
            return;
        }

        try {
            Path file = storageService.resolve(snapshot.storagePath());
            ExtractedText extracted = textExtractionService.extract(file, snapshot.contentType(), snapshot.fileName());
            long extractionMs = timer.mark();

            List<TextChunk> chunks = textChunker.chunk(extracted);
            long chunkingMs = timer.mark();
            if (chunks.isEmpty()) {
                throw new ApiException(com.enterpriseai.hub.exception.ErrorCode.TEXT_EXTRACTION_FAILED,
                        "The document produced no indexable passages");
            }

            List<float[]> embeddings = embedInBatches(chunks);
            long embeddingMs = timer.mark();

            transactionTemplate.executeWithoutResult(status -> {
                vectorSearchRepository.deleteByDocumentId(documentId);
                vectorSearchRepository.insertChunks(documentId, chunks, embeddings);
            });
            long persistenceMs = timer.mark();

            // Enrichment is best effort: a summary is a nice-to-have, an indexed document
            // is the deliverable. An AI outage must not fail ingestion.
            String fullText = joinForAnalysis(extracted);
            DocumentCategory category = documentAiService.classifySafely(snapshot.title(), fullText);
            String summary = documentAiService.summariseSafely(snapshot.title(), fullText);
            long enrichmentMs = timer.mark();

            transactionTemplate.executeWithoutResult(status -> {
                Document document = documentRepository.findById(documentId).orElseThrow();
                document.setCategory(category);
                document.setSummary(summary);
                document.markReady(chunks.size(), extracted.characterCount(),
                        extracted.pageCount(), timer.totalMs());
                documentRepository.save(document);
            });

            log.info("Ingestion finished for document {}: {} chunks, {} characters "
                            + "(extract={}ms chunk={}ms embed={}ms store={}ms enrich={}ms total={}ms)",
                    documentId, chunks.size(), extracted.characterCount(),
                    extractionMs, chunkingMs, embeddingMs, persistenceMs, enrichmentMs, timer.totalMs());

        } catch (Exception ex) {
            String reason = ex instanceof ApiException apiException
                    ? apiException.getMessage()
                    : "Unexpected failure during processing: " + ex.getClass().getSimpleName();
            log.error("Ingestion failed for document {} after {}ms", documentId, timer.totalMs(), ex);

            transactionTemplate.executeWithoutResult(status ->
                    documentRepository.findById(documentId).ifPresent(document -> {
                        document.markFailed(reason);
                        documentRepository.save(document);
                    }));
        }
    }

    /**
     * Embeds in provider-sized batches. One request per chunk would multiply latency and
     * hit rate limits immediately; one request for everything would exceed the provider's
     * per-request input limit on any sizeable document.
     */
    private List<float[]> embedInBatches(List<TextChunk> chunks) {
        int batchSize = Math.max(1, properties.getAi().getEmbeddingBatchSize());
        List<float[]> embeddings = new ArrayList<>(chunks.size());

        for (int start = 0; start < chunks.size(); start += batchSize) {
            int end = Math.min(chunks.size(), start + batchSize);
            List<String> batch = chunks.subList(start, end).stream().map(TextChunk::content).toList();
            List<float[]> batchEmbeddings = embeddingClient.embedAll(batch);

            if (batchEmbeddings.size() != batch.size()) {
                throw new IllegalStateException("Embedding provider returned " + batchEmbeddings.size()
                        + " vectors for " + batch.size() + " inputs");
            }
            embeddings.addAll(batchEmbeddings);
            log.debug("Embedded chunks {}-{} of {}", start, end - 1, chunks.size());
        }

        int expected = properties.getAi().getEmbeddingDimensions();
        if (!embeddings.isEmpty() && embeddings.get(0).length != expected) {
            throw new IllegalStateException("Embedding dimension mismatch: model returned "
                    + embeddings.get(0).length + " but the vector column expects " + expected);
        }
        return embeddings;
    }

    /** Caps how much text is sent to the LLM for summarisation/classification. */
    private String joinForAnalysis(ExtractedText extracted) {
        StringBuilder builder = new StringBuilder();
        for (var page : extracted.pages()) {
            if (builder.length() >= 12_000) {
                break;
            }
            builder.append(page.text()).append("\n\n");
        }
        String text = builder.toString();
        return text.length() <= 12_000 ? text : text.substring(0, 12_000);
    }

    public boolean isReprocessable(DocumentStatus status) {
        return status == DocumentStatus.FAILED || status == DocumentStatus.READY;
    }

    private record DocumentSnapshot(Long id, String storagePath, String contentType, String fileName, String title) {
    }
}
