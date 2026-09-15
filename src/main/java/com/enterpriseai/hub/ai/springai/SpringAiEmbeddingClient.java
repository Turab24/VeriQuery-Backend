package com.enterpriseai.hub.ai.springai;

import com.enterpriseai.hub.ai.EmbeddingClient;
import com.enterpriseai.hub.exception.AiServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;

/**
 * Spring AI backed embeddings.
 *
 * <p>The dimension is taken from configuration rather than probed from the provider:
 * {@code EmbeddingModel#dimensions()} issues a live request, and the value must anyway
 * agree with the {@code vector(n)} column created by Flyway.</p>
 */
@Slf4j
public class SpringAiEmbeddingClient implements EmbeddingClient {

    private final EmbeddingModel embeddingModel;
    private final int dimensions;
    private final String modelName;

    public SpringAiEmbeddingClient(EmbeddingModel embeddingModel, int dimensions, String modelName) {
        this.embeddingModel = embeddingModel;
        this.dimensions = dimensions;
        this.modelName = modelName;
    }

    @Override
    public float[] embed(String text) {
        try {
            return embeddingModel.embed(text);
        } catch (RuntimeException ex) {
            log.error("Embedding request failed for model {}", modelName, ex);
            throw new AiServiceException("The embedding model is currently unavailable", ex);
        }
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }
        try {
            return embeddingModel.embed(texts);
        } catch (RuntimeException ex) {
            log.error("Batch embedding request failed for model {} ({} inputs)", modelName, texts.size(), ex);
            throw new AiServiceException("The embedding model is currently unavailable", ex);
        }
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public String modelName() {
        return modelName;
    }
}
