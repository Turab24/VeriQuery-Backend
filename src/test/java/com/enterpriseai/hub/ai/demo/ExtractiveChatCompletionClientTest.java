package com.enterpriseai.hub.ai.demo;

import com.enterpriseai.hub.ai.PromptTemplates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractiveChatCompletionClientTest {

    private final ExtractiveChatCompletionClient client = new ExtractiveChatCompletionClient();

    private String prompt(String context, String question) {
        return PromptTemplates.RAG_USER_PROMPT_TEMPLATE.formatted(context, question);
    }

    @Test
    @DisplayName("answers are quoted from the supplied passages and carry their citation markers")
    void answersFromContextWithCitations() {
        String context = """
                [1] Cheque Procedures, page 12
                A stop payment instruction must be confirmed in writing at the branch before the cheque is flagged.

                [2] Account Opening, page 3
                Customer onboarding requires two forms of identity verification and a signed mandate.
                """;

        String answer = client.complete(PromptTemplates.RAG_SYSTEM_PROMPT,
                prompt(context, "How is a stop payment instruction confirmed?"));

        assertThat(answer).contains("confirmed in writing");
        assertThat(answer).contains("[1]");
    }

    @Test
    @DisplayName("with no passages it says the knowledge base does not cover the question")
    void refusesWithoutContext() {
        String answer = client.complete(PromptTemplates.NO_CONTEXT_SYSTEM_PROMPT, "QUESTION: anything at all");

        assertThat(answer).containsIgnoringCase("could not find");
    }

    @Test
    @DisplayName("it never invents content that is absent from the passages")
    void doesNotInventContent() {
        String context = """
                [1] Tariff Schedule, page 1
                The standard cheque book fee is 250 units per booklet of 25 leaves.
                """;

        String answer = client.complete(PromptTemplates.RAG_SYSTEM_PROMPT,
                prompt(context, "What is the cheque book fee?"));

        assertThat(answer).contains("250 units");
        assertThat(answer).doesNotContain("approximately");
    }
}
