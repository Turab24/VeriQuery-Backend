package com.enterpriseai.hub.rag;

import com.enterpriseai.hub.ai.EmbeddingClient;
import com.enterpriseai.hub.config.AppProperties;
import com.enterpriseai.hub.repository.VectorSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentRetrieverTest {

    @Mock
    private EmbeddingClient embeddingClient;
    @Mock
    private VectorSearchRepository vectorSearchRepository;

    private DocumentRetriever retriever;

    private static RetrievedChunk chunk(long id, long documentId, double similarity) {
        return new RetrievedChunk(id, documentId, "Doc " + documentId, "doc" + documentId + ".pdf",
                0, 1, null, "content " + id, similarity);
    }

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.getRag().setTopK(4);
        properties.getRag().setCandidateMultiplier(4);
        properties.getRag().setMinSimilarity(0.30);
        retriever = new DocumentRetriever(embeddingClient, vectorSearchRepository, properties);
        when(embeddingClient.embed(any())).thenReturn(new float[]{1f, 0f});
    }

    @Test
    @DisplayName("passages below the similarity floor are discarded")
    void appliesSimilarityFloor() {
        when(vectorSearchRepository.search(any(), anyInt(), isNull())).thenReturn(List.of(
                chunk(1, 10, 0.81),
                chunk(2, 10, 0.42),
                chunk(3, 11, 0.29),
                chunk(4, 11, 0.05)));

        RetrievalResult result = retriever.retrieve("how do I stop a cheque?", null);

        assertThat(result.chunks()).hasSize(2);
        assertThat(result.chunks()).extracting(RetrievedChunk::chunkId).containsExactly(1L, 2L);
        assertThat(result.candidatesConsidered()).isEqualTo(4);
    }

    @Test
    @DisplayName("an irrelevant question returns nothing instead of the nearest unrelated passage")
    void returnsEmptyWhenNothingIsRelevant() {
        when(vectorSearchRepository.search(any(), anyInt(), isNull()))
                .thenReturn(List.of(chunk(1, 10, 0.11), chunk(2, 11, 0.04)));

        RetrievalResult result = retriever.retrieve("what is the capital of France?", null);

        assertThat(result.isEmpty()).isTrue();
        assertThat(result.confidenceLabel()).isEqualTo("NONE");
    }

    @Test
    @DisplayName("one long document cannot fill the whole context window")
    void diversifiesAcrossDocuments() {
        when(vectorSearchRepository.search(any(), anyInt(), isNull())).thenReturn(List.of(
                chunk(1, 10, 0.90),
                chunk(2, 10, 0.88),
                chunk(3, 10, 0.86),
                chunk(4, 10, 0.84),
                chunk(5, 11, 0.60),
                chunk(6, 12, 0.55)));

        RetrievalResult result = retriever.retrieve("payment limits", null);

        assertThat(result.chunks()).hasSize(4);
        assertThat(result.chunks()).extracting(RetrievedChunk::documentId)
                .containsExactlyInAnyOrder(10L, 10L, 11L, 12L);
    }

    @Test
    @DisplayName("results are ordered by similarity and the confidence label follows the top score")
    void ordersBySimilarity() {
        when(vectorSearchRepository.search(any(), anyInt(), isNull())).thenReturn(List.of(
                chunk(1, 10, 0.45),
                chunk(2, 11, 0.72)));

        RetrievalResult result = retriever.retrieve("transaction limits", null);

        assertThat(result.chunks()).extracting(RetrievedChunk::similarity)
                .containsExactly(0.72, 0.45);
        assertThat(result.topSimilarity()).isEqualTo(0.72);
        assertThat(result.confidenceLabel()).isEqualTo("HIGH");
    }
}
