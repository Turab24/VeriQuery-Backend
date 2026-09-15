package com.enterpriseai.hub.service;

import com.enterpriseai.hub.ai.ChatCompletionClient;
import com.enterpriseai.hub.ai.PromptTemplates;
import com.enterpriseai.hub.config.AppProperties;
import com.enterpriseai.hub.domain.Conversation;
import com.enterpriseai.hub.domain.Document;
import com.enterpriseai.hub.domain.User;
import com.enterpriseai.hub.dto.chat.ChatRequest;
import com.enterpriseai.hub.dto.chat.ChatResponse;
import com.enterpriseai.hub.rag.ContextBuilder;
import com.enterpriseai.hub.rag.DocumentRetriever;
import com.enterpriseai.hub.rag.RetrievalResult;
import com.enterpriseai.hub.rag.RetrievedChunk;
import com.enterpriseai.hub.repository.ConversationRepository;
import com.enterpriseai.hub.repository.DocumentRepository;
import com.enterpriseai.hub.repository.MessageRepository;
import com.enterpriseai.hub.repository.UserRepository;
import com.enterpriseai.hub.security.AppUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatServiceTest {

    @Mock
    private DocumentRetriever documentRetriever;
    @Mock
    private ChatCompletionClient chatCompletionClient;
    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditService auditService;

    private ChatService chatService;
    private AppProperties properties;

    private static RetrievedChunk chunk(long id, long documentId, double similarity, String content) {
        return new RetrievedChunk(id, documentId, "Cheque Procedures", "cheques.pdf",
                0, 12, "Stop Payment", content, similarity);
    }

    @BeforeEach
    void setUp() {
        properties = new AppProperties();
        properties.getRag().setMaxContextCharacters(8000);
        properties.getRag().setIncludeConversationHistory(false);

        chatService = new ChatService(documentRetriever, new ContextBuilder(properties), chatCompletionClient,
                conversationRepository, messageRepository, documentRepository, userRepository,
                properties, auditService);

        AppUserPrincipal principal = new AppUserPrincipal(7L, "analyst@acme.test", "Analyst", "", true,
                Set.of("ROLE_USER"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        User user = new User();
        user.setId(7L);
        user.setEmail("analyst@acme.test");
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        Conversation conversation = new Conversation();
        conversation.setId(100L);
        conversation.setUser(user);
        conversation.setTitle("New conversation");
        when(conversationRepository.save(any(Conversation.class))).thenReturn(conversation);
        when(conversationRepository.saveAndFlush(any(Conversation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationRepository.findByIdAndUserId(anyLong(), anyLong())).thenReturn(Optional.of(conversation));

        Document document = new Document();
        document.setId(55L);
        document.setTitle("Cheque Procedures");
        when(documentRepository.getReferenceById(anyLong())).thenReturn(document);

        when(chatCompletionClient.modelName()).thenReturn("test-model");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("a grounded answer carries one citation per supplied passage, ranked by similarity")
    void returnsCitationsForRetrievedPassages() {
        when(documentRetriever.retrieve(any(), any())).thenReturn(new RetrievalResult(List.of(
                chunk(1, 55, 0.82, "A stop payment instruction must be confirmed in writing."),
                chunk(2, 55, 0.61, "The branch manager acknowledges the instruction within one day.")),
                12, 8, 10));
        when(chatCompletionClient.complete(any(), any())).thenReturn("Confirm in writing [1] and wait one day [2].");

        ChatResponse response = chatService.ask(new ChatRequest("How do I stop a cheque?", null, null));

        assertThat(response.citations()).hasSize(2);
        assertThat(response.citations().get(0).rank()).isEqualTo(1);
        assertThat(response.citations().get(0).pageNumber()).isEqualTo(12);
        assertThat(response.citations().get(0).documentId()).isEqualTo(55L);
        assertThat(response.retrievalConfidence()).isEqualTo("HIGH");
        assertThat(response.retrievedChunks()).isEqualTo(2);
        assertThat(response.model()).isEqualTo("test-model");
        assertThat(response.timings().retrievalMs()).isEqualTo(8);
        assertThat(response.timings().embeddingMs()).isEqualTo(12);
    }

    @Test
    @DisplayName("the retrieved passages are handed to the model as numbered context")
    void buildsNumberedContextPrompt() {
        when(documentRetriever.retrieve(any(), any())).thenReturn(new RetrievalResult(List.of(
                chunk(1, 55, 0.82, "A stop payment instruction must be confirmed in writing.")), 5, 5, 4));
        when(chatCompletionClient.complete(any(), any())).thenReturn("Answer [1].");

        chatService.ask(new ChatRequest("How do I stop a cheque?", null, null));

        ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
        verify(chatCompletionClient).complete(systemPrompt.capture(), userPrompt.capture());

        assertThat(systemPrompt.getValue()).isEqualTo(PromptTemplates.RAG_SYSTEM_PROMPT);
        assertThat(userPrompt.getValue()).contains("[1] Cheque Procedures, page 12");
        assertThat(userPrompt.getValue()).contains("A stop payment instruction must be confirmed in writing.");
        assertThat(userPrompt.getValue()).contains("QUESTION: How do I stop a cheque?");
    }

    @Test
    @DisplayName("when nothing clears the similarity floor the model is told to refuse, not to answer")
    void refusesWhenNothingRetrieved() {
        when(documentRetriever.retrieve(any(), any())).thenReturn(RetrievalResult.empty(4, 3, 6));
        when(chatCompletionClient.complete(eq(PromptTemplates.NO_CONTEXT_SYSTEM_PROMPT), any()))
                .thenReturn("The indexed documents do not cover this.");

        ChatResponse response = chatService.ask(new ChatRequest("Unrelated question", null, null));

        assertThat(response.citations()).isEmpty();
        assertThat(response.retrievalConfidence()).isEqualTo("NONE");
        assertThat(response.topSimilarity()).isZero();
        verify(chatCompletionClient, never()).complete(eq(PromptTemplates.RAG_SYSTEM_PROMPT), any());
    }

    @Test
    @DisplayName("a new conversation gets a title derived from the first question")
    void derivesConversationTitle() {
        when(documentRetriever.retrieve(any(), any())).thenReturn(RetrievalResult.empty(1, 1, 0));
        when(chatCompletionClient.complete(any(), any())).thenReturn("No match.");

        chatService.ask(new ChatRequest("What is the process for requesting a cheque book?", null, null));

        ArgumentCaptor<Conversation> saved = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository).save(saved.capture());
        assertThat(saved.getValue().getTitle()).isEqualTo("What is the process for requesting a cheque book?");
    }

    @Test
    @DisplayName("both turns are persisted with the assistant's timings")
    void persistsBothTurnsWithTimings() {
        when(documentRetriever.retrieve(any(), any())).thenReturn(new RetrievalResult(List.of(
                chunk(1, 55, 0.75, "Relevant content about cheque books.")), 9, 6, 3));
        when(chatCompletionClient.complete(any(), any())).thenReturn("Answer [1].");

        chatService.ask(new ChatRequest("How do I request a cheque book?", null, null));

        ArgumentCaptor<Conversation> flushed = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository).saveAndFlush(flushed.capture());

        assertThat(flushed.getValue().getMessages()).hasSize(2);
        var assistant = flushed.getValue().getMessages().get(1);
        assertThat(assistant.getRole().name()).isEqualTo("ASSISTANT");
        assertThat(assistant.getRetrievalMs()).isEqualTo(15);
        assertThat(assistant.getTotalMs()).isNotNull();
        assertThat(assistant.getCitations()).hasSize(1);
        assertThat(assistant.getCitations().get(0).getCitationRank()).isEqualTo(1);
    }
}
