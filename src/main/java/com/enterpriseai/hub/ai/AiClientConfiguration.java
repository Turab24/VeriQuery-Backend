package com.enterpriseai.hub.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.enterpriseai.hub.ai.claude.ClaudeChatCompletionClient;
import com.enterpriseai.hub.ai.demo.DeterministicEmbeddingClient;
import com.enterpriseai.hub.ai.demo.ExtractiveChatCompletionClient;
import com.enterpriseai.hub.ai.springai.SpringAiChatCompletionClient;
import com.enterpriseai.hub.ai.springai.SpringAiEmbeddingClient;
import com.enterpriseai.hub.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Selects the AI provider at startup.
 *
 * <pre>
 * app.ai.provider=openai  -&gt; Spring AI ChatClient + EmbeddingModel (needs OPENAI_API_KEY)
 * app.ai.provider=claude  -&gt; Anthropic Messages API for chat (needs ANTHROPIC_API_KEY);
 *                            embeddings fall back to the demo hashing client because
 *                            Anthropic has no embeddings endpoint
 * app.ai.provider=demo    -&gt; deterministic local implementations (no network, no key)
 * </pre>
 *
 * Everything downstream depends only on {@link ChatCompletionClient} and
 * {@link EmbeddingClient}, so switching providers is a configuration change.
 */
@Slf4j
@Configuration
public class AiClientConfiguration {

    @Bean
    @ConditionalOnProperty(name = "app.ai.provider", havingValue = "openai")
    public ChatCompletionClient openAiChatCompletionClient(ChatClient.Builder chatClientBuilder,
                                                           AppProperties properties) {
        log.info("AI provider: Spring AI chat model '{}'", properties.getAi().getChatModel());
        return new SpringAiChatCompletionClient(chatClientBuilder, properties.getAi().getChatModel());
    }

    @Bean
    @ConditionalOnProperty(name = "app.ai.provider", havingValue = "openai")
    public EmbeddingClient openAiEmbeddingClient(EmbeddingModel embeddingModel, AppProperties properties) {
        log.info("AI provider: Spring AI embedding model '{}' ({} dimensions)",
                properties.getAi().getEmbeddingModel(), properties.getAi().getEmbeddingDimensions());
        return new SpringAiEmbeddingClient(embeddingModel,
                properties.getAi().getEmbeddingDimensions(),
                properties.getAi().getEmbeddingModel());
    }

    @Bean
    @ConditionalOnProperty(name = "app.ai.provider", havingValue = "claude")
    public ChatCompletionClient claudeChatCompletionClient(AppProperties properties) {
        AppProperties.Ai ai = properties.getAi();
        log.info("AI provider: Claude chat model '{}'", ai.getAnthropicChatModel());
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(ai.getAnthropicApiKey())
                .build();
        return new ClaudeChatCompletionClient(client, ai.getAnthropicChatModel(), ai.getAnthropicMaxTokens());
    }

    @Bean
    @ConditionalOnProperty(name = "app.ai.provider", havingValue = "claude")
    public EmbeddingClient claudeEmbeddingClient(AppProperties properties) {
        log.warn("AI provider: CLAUDE chat + DEMO hashing embeddings ({} dimensions) - Anthropic has no "
                        + "embeddings API. Retrieval is lexical, not semantic.",
                properties.getAi().getEmbeddingDimensions());
        return new DeterministicEmbeddingClient(properties.getAi().getEmbeddingDimensions());
    }

    @Bean
    @ConditionalOnProperty(name = "app.ai.provider", havingValue = "demo", matchIfMissing = true)
    public ChatCompletionClient demoChatCompletionClient() {
        log.warn("AI provider: DEMO extractive reader. Answers are quoted from retrieved passages, "
                + "not generated. Set AI_PROVIDER=openai and OPENAI_API_KEY for real generation.");
        return new ExtractiveChatCompletionClient();
    }

    @Bean
    @ConditionalOnProperty(name = "app.ai.provider", havingValue = "demo", matchIfMissing = true)
    public EmbeddingClient demoEmbeddingClient(AppProperties properties) {
        log.warn("AI provider: DEMO hashing embeddings ({} dimensions). Retrieval is lexical, not semantic.",
                properties.getAi().getEmbeddingDimensions());
        return new DeterministicEmbeddingClient(properties.getAi().getEmbeddingDimensions());
    }
}
