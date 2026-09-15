package com.enterpriseai.hub.ai.demo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicEmbeddingClientTest {

    private final DeterministicEmbeddingClient client = new DeterministicEmbeddingClient(1536);

    private static double cosine(float[] a, float[] b) {
        double dot = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
        }
        return dot;
    }

    @Test
    @DisplayName("embeddings are stable across calls - indexed chunks must keep matching later queries")
    void isDeterministic() {
        float[] first = client.embed("Cheque stop payment procedure");
        float[] second = client.embed("Cheque stop payment procedure");

        assertThat(first).containsExactly(second);
    }

    @Test
    @DisplayName("vectors have the configured dimension and unit length")
    void producesNormalisedVectorsOfTheRightSize() {
        float[] vector = client.embed("Customer onboarding requires identity verification");

        assertThat(vector).hasSize(1536);
        assertThat(cosine(vector, vector)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-4));
    }

    @Test
    @DisplayName("related text scores higher than unrelated text")
    void ranksRelatedTextHigher() {
        float[] query = client.embed("cheque book request process");
        float[] related = client.embed("To request a cheque book the customer submits a request at the branch.");
        float[] unrelated = client.embed("Kubernetes ingress controllers terminate TLS at the edge.");

        assertThat(cosine(query, related)).isGreaterThan(cosine(query, unrelated));
    }

    @Test
    @DisplayName("blank input still yields a usable unit vector - pgvector rejects all-zero vectors")
    void handlesBlankInput() {
        float[] vector = client.embed("   ");

        assertThat(vector).hasSize(1536);
        assertThat(cosine(vector, vector)).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("batch embedding preserves input order")
    void batchPreservesOrder() {
        List<String> inputs = List.of("alpha document", "beta document", "gamma document");

        List<float[]> vectors = client.embedAll(inputs);

        assertThat(vectors).hasSize(3);
        for (int i = 0; i < inputs.size(); i++) {
            assertThat(vectors.get(i)).containsExactly(client.embed(inputs.get(i)));
        }
    }
}
