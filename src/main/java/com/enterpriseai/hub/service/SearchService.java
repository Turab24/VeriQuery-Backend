package com.enterpriseai.hub.service;

import com.enterpriseai.hub.dto.search.SearchResultResponse;
import com.enterpriseai.hub.rag.DocumentRetriever;
import com.enterpriseai.hub.rag.RetrievalResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Semantic search over the knowledge base.
 *
 * <p>This is retrieval without generation: the same embedding and vector search the chat
 * pipeline uses, returned directly. It answers "which document covers this?" rather than
 * "what is the answer?", which is why it exists as its own endpoint - it is faster, costs
 * no generation tokens, and gives the user the sources to read themselves.</p>
 *
 * <p>Because matching is by vector proximity rather than by keyword, "how do I stop a
 * cheque?" retrieves a passage titled "Cheque Stop Payment Procedure" even though the two
 * share almost no literal terms.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private static final int EXCERPT_LENGTH = 320;
    private static final int MAX_RESULTS = 25;

    private final DocumentRetriever documentRetriever;

    public SearchResultResponse search(String query, Integer limit, List<Long> documentIds) {
        int topK = limit == null ? 10 : Math.clamp(limit, 1, MAX_RESULTS);
        RetrievalResult retrieval = documentRetriever.retrieve(query, documentIds, topK);

        List<SearchResultResponse.Hit> hits = retrieval.chunks().stream()
                .map(chunk -> new SearchResultResponse.Hit(
                        chunk.chunkId(),
                        chunk.documentId(),
                        chunk.documentTitle(),
                        chunk.fileName(),
                        chunk.pageNumber(),
                        chunk.section(),
                        chunk.excerpt(EXCERPT_LENGTH),
                        chunk.similarity()))
                .toList();

        log.debug("Semantic search '{}' returned {} hit(s)", query, hits.size());
        return new SearchResultResponse(query, hits.size(), retrieval.embeddingMs(), retrieval.searchMs(), hits);
    }
}
