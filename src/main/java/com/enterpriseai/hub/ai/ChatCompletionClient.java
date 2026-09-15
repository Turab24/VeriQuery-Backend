package com.enterpriseai.hub.ai;

/**
 * Port for text generation.
 *
 * <p>The RAG pipeline depends on this narrow interface rather than on a concrete vendor
 * SDK. That keeps three things possible without touching business logic: swapping the
 * model provider, running the whole platform offline in demo mode, and unit-testing the
 * pipeline with a stub instead of a network call.</p>
 */
public interface ChatCompletionClient {

    /**
     * @param systemPrompt instructions and guard rails for the model
     * @param userPrompt   the question together with the retrieved context
     * @return the generated answer
     * @throws com.enterpriseai.hub.exception.AiServiceException if the model cannot be reached
     */
    String complete(String systemPrompt, String userPrompt);

    /** Identifier recorded on every generated message, e.g. {@code gpt-4o-mini}. */
    String modelName();
}
