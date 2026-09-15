package com.enterpriseai.hub.ai.claude;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.enterpriseai.hub.ai.ChatCompletionClient;
import com.enterpriseai.hub.exception.AiServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.stream.Collectors;

/**
 * Generation backed directly by the Anthropic Messages API. Anthropic offers no
 * OpenAI-compatible endpoint and no embeddings API, so this talks to the official
 * {@code anthropic-java} SDK rather than going through Spring AI, and is paired with a
 * non-Anthropic {@link com.enterpriseai.hub.ai.EmbeddingClient} (demo or OpenAI).
 */
@Slf4j
public class ClaudeChatCompletionClient implements ChatCompletionClient {

    private final AnthropicClient client;
    private final String modelName;
    private final long maxTokens;

    public ClaudeChatCompletionClient(AnthropicClient client, String modelName, long maxTokens) {
        this.client = client;
        this.modelName = modelName;
        this.maxTokens = maxTokens;
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        try {
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(modelName)
                    .maxTokens(maxTokens)
                    .system(systemPrompt)
                    .addUserMessage(userPrompt)
                    .build();

            Message response = client.messages().create(params);
            String content = response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(textBlock -> textBlock.text())
                    .collect(Collectors.joining());

            if (!StringUtils.hasText(content)) {
                throw new AiServiceException("The chat model returned an empty response");
            }
            return content;
        } catch (AiServiceException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Chat completion failed against model {}", modelName, ex);
            throw new AiServiceException("The chat model is currently unavailable", ex);
        }
    }

    @Override
    public String modelName() {
        return modelName;
    }
}
