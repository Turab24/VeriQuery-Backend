package com.enterpriseai.hub.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * Strongly typed application configuration. Every value is sourced from
 * {@code application.yml} which in turn reads environment variables, so no secret
 * or environment specific value is ever compiled into the artifact.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Jwt jwt = new Jwt();
    private final Cors cors = new Cors();
    private final Storage storage = new Storage();
    private final Ai ai = new Ai();
    private final Rag rag = new Rag();
    private final Chunking chunking = new Chunking();
    private final Processing processing = new Processing();

    @Getter
    @Setter
    public static class Jwt {
        /** Base64 or raw secret, at least 32 bytes once decoded. */
        @NotBlank
        private String secret;
        @NotBlank
        private String issuer = "enterprise-ai-hub";
        private Duration accessTokenTtl = Duration.ofMinutes(30);
        private Duration refreshTokenTtl = Duration.ofDays(14);
    }

    @Getter
    @Setter
    public static class Cors {
        @NotEmpty
        private List<String> allowedOrigins = List.of("http://localhost:4200");
    }

    @Getter
    @Setter
    public static class Storage {
        @NotBlank
        private String location = "./data/documents";
        @Min(1)
        private long maxFileSizeBytes = 26_214_400L;
        @NotEmpty
        private List<String> allowedContentTypes = List.of("application/pdf", "text/plain");
        @NotEmpty
        private List<String> allowedExtensions = List.of("pdf", "txt", "md", "docx");
    }

    @Getter
    @Setter
    public static class Ai {
        /** {@code demo}, {@code openai}, or {@code claude}. */
        @NotBlank
        private String provider = "demo";
        @Min(8)
        private int embeddingDimensions = 1536;
        private String chatModel = "gpt-4o-mini";
        private String embeddingModel = "text-embedding-3-small";
        @Min(1)
        private int embeddingBatchSize = 32;
        @Min(0)
        private int maxConversationHistoryMessages = 8;
        /** Anthropic API key. Only required when {@code provider=claude}. */
        private String anthropicApiKey;
        private String anthropicChatModel = "claude-opus-5";
        @Min(1)
        private int anthropicMaxTokens = 4096;
    }

    @Getter
    @Setter
    public static class Rag {
        @Min(1)
        private int topK = 6;
        /**
         * The vector search fetches {@code topK * candidateMultiplier} candidates before
         * per-document diversification, so a single long document cannot monopolise the
         * context window.
         */
        @Min(1)
        private int candidateMultiplier = 4;
        private double minSimilarity = 0.15;
        @Min(500)
        private int maxContextCharacters = 12_000;
        private boolean includeConversationHistory = true;
    }

    @Getter
    @Setter
    public static class Chunking {
        @Min(200)
        private int targetCharacters = 1200;
        @Min(0)
        private int overlapCharacters = 180;
        @Min(1)
        private int minCharacters = 120;
    }

    @Getter
    @Setter
    public static class Processing {
        @Min(1)
        private int corePoolSize = 2;
        @Min(1)
        private int maxPoolSize = 4;
        @Min(1)
        private int queueCapacity = 100;
    }
}
