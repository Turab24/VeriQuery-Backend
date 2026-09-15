package com.enterpriseai.hub.rag;

import com.enterpriseai.hub.ai.EmbeddingClient;
import com.enterpriseai.hub.config.AppProperties;
import com.enterpriseai.hub.observability.StageTimer;
import com.enterpriseai.hub.repository.VectorSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The retrieval half of the RAG pipeline.
 *
 * <pre>
 * question --&gt; embedding --&gt; pgvector ANN search --&gt; threshold --&gt; diversify --&gt; top-K
 * </pre>
 *
 * <p>Two refinements sit between the raw vector search and the model:</p>
 * <ul>
 *   <li><b>Similarity floor.</b> An ANN index always returns its K nearest neighbours, even
 *       when nothing is actually relevant. Without a floor, an unrelated question would be
 *       answered from whatever happened to be closest - the classic way a RAG system
 *       hallucinates with full confidence. Below the floor the retriever returns nothing and
 *       the pipeline says so.</li>
 *   <li><b>Per-document diversification.</b> The search over-fetches
 *       ({@code topK * candidateMultiplier}) and then caps how many passages one document
 *       may contribute, so a long document cannot crowd out a short one that answers the
 *       question better. Remaining slots are filled from the leftovers by score.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentRetriever {

    private final EmbeddingClient embeddingClient;
    private final VectorSearchRepository vectorSearchRepository;
    private final AppProperties properties;

    public RetrievalResult retrieve(String question, List<Long> documentIds) {
        return retrieve(question, documentIds, properties.getRag().getTopK());
    }

    public RetrievalResult retrieve(String question, List<Long> documentIds, int topK) {
        AppProperties.Rag config = properties.getRag();
        StageTimer timer = StageTimer.start();

        float[] queryEmbedding = embeddingClient.embed(question);
        long embeddingMs = timer.mark();

        int candidateLimit = Math.max(topK, topK * config.getCandidateMultiplier());
        List<RetrievedChunk> candidates = vectorSearchRepository.search(queryEmbedding, candidateLimit, documentIds);
        long searchMs = timer.mark();

        List<RetrievedChunk> relevant = candidates.stream()
                .filter(chunk -> chunk.similarity() >= config.getMinSimilarity())
                .sorted(Comparator.comparingDouble(RetrievedChunk::similarity).reversed())
                .toList();

        if (relevant.isEmpty()) {
            log.info("Retrieval found no passage above the similarity floor {} (searched {} candidates) in {}ms",
                    config.getMinSimilarity(), candidates.size(), embeddingMs + searchMs);
            return RetrievalResult.empty(embeddingMs, searchMs, candidates.size());
        }

        List<RetrievedChunk> selected = diversify(relevant, topK);
        log.info("Retrieved {}/{} passages (top similarity {}) embedding={}ms search={}ms",
                selected.size(), candidates.size(),
                String.format("%.3f", selected.get(0).similarity()), embeddingMs, searchMs);

        return new RetrievalResult(selected, embeddingMs, searchMs, candidates.size());
    }

    private List<RetrievedChunk> diversify(List<RetrievedChunk> ranked, int topK) {
        int maxPerDocument = Math.max(2, (int) Math.ceil(topK / 2.0));
        Map<Long, Integer> perDocument = new HashMap<>();
        List<RetrievedChunk> selected = new ArrayList<>(topK);
        List<RetrievedChunk> overflow = new ArrayList<>();

        for (RetrievedChunk chunk : ranked) {
            if (selected.size() >= topK) {
                break;
            }
            int used = perDocument.getOrDefault(chunk.documentId(), 0);
            if (used < maxPerDocument) {
                selected.add(chunk);
                perDocument.put(chunk.documentId(), used + 1);
            } else {
                overflow.add(chunk);
            }
        }

        for (RetrievedChunk chunk : overflow) {
            if (selected.size() >= topK) {
                break;
            }
            selected.add(chunk);
        }

        selected.sort(Comparator.comparingDouble(RetrievedChunk::similarity).reversed());
        return selected;
    }
}
