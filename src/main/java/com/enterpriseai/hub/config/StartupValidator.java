package com.enterpriseai.hub.config;

import com.enterpriseai.hub.ai.ChatCompletionClient;
import com.enterpriseai.hub.ai.EmbeddingClient;
import com.enterpriseai.hub.repository.VectorSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Fails fast on configuration that would otherwise surface much later as a confusing
 * runtime error, and prints the effective AI configuration so a running instance can be
 * identified from its logs alone.
 *
 * <p>The important check is the embedding dimension: the {@code vector(n)} column is fixed
 * by the Flyway migration, so pointing the application at a model with a different output
 * size would fail on the first insert, halfway through a user's upload.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartupValidator implements ApplicationRunner {

    private final ChatCompletionClient chatCompletionClient;
    private final EmbeddingClient embeddingClient;
    private final VectorSearchRepository vectorSearchRepository;
    private final AppProperties properties;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        int configured = properties.getAi().getEmbeddingDimensions();
        int clientDimensions = embeddingClient.dimensions();
        int columnDimensions = vectorSearchRepository.embeddingColumnDimensions();

        if (clientDimensions != configured) {
            throw new IllegalStateException("Embedding client reports " + clientDimensions
                    + " dimensions but app.ai.embedding-dimensions is " + configured);
        }
        if (columnDimensions > 0 && columnDimensions != configured) {
            throw new IllegalStateException("The document_chunks.embedding column is vector("
                    + columnDimensions + ") but the configured embedding model produces " + configured
                    + " dimensions. Re-index the knowledge base with a migration that recreates the column.");
        }

        if (isDefaultJwtSecretInProduction()) {
            throw new IllegalStateException(
                    "The development JWT secret is still in use while the 'prod' profile is active. "
                            + "Set the JWT_SECRET environment variable.");
        }

        log.info("EnterpriseAI Hub started | profiles={} | ai.provider={} | chat={} | embedding={} ({} dims) | "
                        + "rag.topK={} rag.minSimilarity={}",
                Arrays.toString(environment.getActiveProfiles()),
                properties.getAi().getProvider(),
                chatCompletionClient.modelName(),
                embeddingClient.modelName(),
                clientDimensions,
                properties.getRag().getTopK(),
                properties.getRag().getMinSimilarity());
    }

    private boolean isDefaultJwtSecretInProduction() {
        boolean production = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        return production && properties.getJwt().getSecret()
                .startsWith("ZGV2LW9ubHktc2VjcmV0");
    }
}
