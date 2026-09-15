package com.enterpriseai.hub.ai.springai;

import com.enterpriseai.hub.ai.ChatCompletionClient;
import com.enterpriseai.hub.exception.AiServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.util.StringUtils;

/**
 * Spring AI backed generation. Uses the fluent {@link ChatClient} API, which is the
 * current recommended entry point in Spring AI 1.0 (the older
 * {@code ChatModel.call(Prompt)} style still works but offers no prompt composition).
 */
@Slf4j
public class SpringAiChatCompletionClient implements ChatCompletionClient {

    private final ChatClient chatClient;
    private final String modelName;

    public SpringAiChatCompletionClient(ChatClient.Builder chatClientBuilder, String modelName) {
        this.chatClient = chatClientBuilder.build();
        this.modelName = modelName;
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        try {
            String content = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();
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
