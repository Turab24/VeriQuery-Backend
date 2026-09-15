package com.enterpriseai.hub.ai.demo;

import com.enterpriseai.hub.ai.EmbeddingClient;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Offline embedding implementation used when {@code app.ai.provider=demo}.
 *
 * <p><b>What it is:</b> a hashing vectoriser. Each input is lower-cased and split into
 * word tokens; every token and every 4-character sub-token n-gram is hashed into a small
 * number of dimensions with signed weights, after which the vector is L2-normalised.
 * Cosine similarity over these vectors therefore measures weighted lexical overlap, with
 * the character n-grams providing tolerance for inflection ("cheque" vs "cheques").</p>
 *
 * <p><b>What it is not:</b> a semantic model. It has no notion that "stop payment" and
 * "cancel a cheque" are related. It exists so the entire ingestion, storage, retrieval
 * and citation pipeline can be run, demonstrated and tested end to end without an API
 * key or network access. Set {@code app.ai.provider=openai} for real semantic retrieval.</p>
 */
public class DeterministicEmbeddingClient implements EmbeddingClient {

    private static final int HASHES_PER_TOKEN = 3;
    private static final int NGRAM_SIZE = 4;
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "a", "an", "and", "or", "of", "to", "in", "for", "on", "is", "are", "be", "by",
            "with", "as", "at", "it", "this", "that", "from", "was", "were", "will", "can", "has", "have");

    private final int dimensions;

    public DeterministicEmbeddingClient(int dimensions) {
        this.dimensions = dimensions;
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[dimensions];
        if (text == null || text.isBlank()) {
            // pgvector rejects an all-zero vector for cosine distance; use a fixed unit vector.
            vector[0] = 1.0f;
            return vector;
        }

        for (String token : tokenize(text)) {
            accumulate(vector, token, 1.0f);
            for (String ngram : characterNgrams(token)) {
                accumulate(vector, ngram, 0.45f);
            }
        }
        return normalize(vector);
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (String text : texts) {
            vectors.add(embed(text));
        }
        return vectors;
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public String modelName() {
        return "demo-hashing-embedding-" + dimensions;
    }

    private List<String> tokenize(String text) {
        String[] rawTokens = text.toLowerCase(Locale.ROOT).split("[^\\p{Alnum}]+");
        List<String> tokens = new ArrayList<>(rawTokens.length);
        for (String token : rawTokens) {
            if (token.length() > 1 && !STOP_WORDS.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private List<String> characterNgrams(String token) {
        if (token.length() <= NGRAM_SIZE) {
            return List.of();
        }
        List<String> ngrams = new ArrayList<>(token.length() - NGRAM_SIZE + 1);
        for (int i = 0; i + NGRAM_SIZE <= token.length(); i++) {
            ngrams.add(token.substring(i, i + NGRAM_SIZE));
        }
        return ngrams;
    }

    private void accumulate(float[] vector, String term, float weight) {
        byte[] bytes = term.getBytes(StandardCharsets.UTF_8);
        for (int hashIndex = 0; hashIndex < HASHES_PER_TOKEN; hashIndex++) {
            long hash = fnv1a(bytes, hashIndex);
            int position = (int) Math.floorMod(hash, dimensions);
            float sign = ((hash >>> 33) & 1L) == 0L ? 1.0f : -1.0f;
            vector[position] += sign * weight;
        }
    }

    private static long fnv1a(byte[] bytes, int seed) {
        long hash = 0xcbf29ce484222325L ^ (seed * 0x9E3779B97F4A7C15L);
        for (byte b : bytes) {
            hash ^= (b & 0xffL);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static float[] normalize(float[] vector) {
        double sumOfSquares = 0.0;
        for (float value : vector) {
            sumOfSquares += (double) value * value;
        }
        if (sumOfSquares == 0.0) {
            vector[0] = 1.0f;
            return vector;
        }
        float norm = (float) Math.sqrt(sumOfSquares);
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= norm;
        }
        return vector;
    }
}
