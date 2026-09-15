package com.enterpriseai.hub.service;

import com.enterpriseai.hub.ai.ChatCompletionClient;
import com.enterpriseai.hub.ai.PromptTemplates;
import com.enterpriseai.hub.config.AppProperties;
import com.enterpriseai.hub.domain.Conversation;
import com.enterpriseai.hub.domain.Document;
import com.enterpriseai.hub.domain.Message;
import com.enterpriseai.hub.domain.MessageCitation;
import com.enterpriseai.hub.domain.MessageRole;
import com.enterpriseai.hub.domain.User;
import com.enterpriseai.hub.dto.chat.ChatRequest;
import com.enterpriseai.hub.dto.chat.ChatResponse;
import com.enterpriseai.hub.dto.chat.CitationResponse;
import com.enterpriseai.hub.exception.ApiException;
import com.enterpriseai.hub.exception.ErrorCode;
import com.enterpriseai.hub.exception.ResourceNotFoundException;
import com.enterpriseai.hub.observability.StageTimer;
import com.enterpriseai.hub.rag.ContextBuilder;
import com.enterpriseai.hub.rag.DocumentRetriever;
import com.enterpriseai.hub.rag.RetrievalResult;
import com.enterpriseai.hub.rag.RetrievedChunk;
import com.enterpriseai.hub.repository.ConversationRepository;
import com.enterpriseai.hub.repository.DocumentRepository;
import com.enterpriseai.hub.repository.MessageRepository;
import com.enterpriseai.hub.repository.UserRepository;
import com.enterpriseai.hub.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The generation half of the RAG pipeline, plus conversation persistence.
 *
 * <pre>
 * question
 *   -&gt; DocumentRetriever   (embed, ANN search, threshold, diversify)
 *   -&gt; ContextBuilder      (numbered passages within the context budget)
 *   -&gt; ChatCompletionClient(system guard rails + context + question)
 *   -&gt; persist message + citations + per-stage timings
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final int EXCERPT_LENGTH = 400;
    private static final int TITLE_MAX_LENGTH = 60;

    private final DocumentRetriever documentRetriever;
    private final ContextBuilder contextBuilder;
    private final ChatCompletionClient chatCompletionClient;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final AppProperties properties;
    private final AuditService auditService;

    @Transactional
    public ChatResponse ask(ChatRequest request) {
        Long userId = SecurityUtils.currentUserId();
        StageTimer timer = StageTimer.start();

        Conversation conversation = resolveConversation(request.conversationId(), userId, request.question());

        RetrievalResult retrieval = documentRetriever.retrieve(request.question(), request.documentIds());
        ContextBuilder.BuiltContext context = contextBuilder.build(retrieval.chunks());

        String answer;
        long llmMs;
        StageTimer llmTimer = StageTimer.start();
        if (retrieval.isEmpty()) {
            // No grounded context: ask the model to say so rather than answering from
            // parametric memory. This is the single most important hallucination guard.
            answer = chatCompletionClient.complete(PromptTemplates.NO_CONTEXT_SYSTEM_PROMPT,
                    "QUESTION: " + request.question());
            llmMs = llmTimer.totalMs();
        } else {
            String userPrompt = recentHistoryBlock(conversation)
                    + PromptTemplates.RAG_USER_PROMPT_TEMPLATE.formatted(context.text(), request.question());
            answer = chatCompletionClient.complete(PromptTemplates.RAG_SYSTEM_PROMPT, userPrompt);
            llmMs = llmTimer.totalMs();
        }

        long totalMs = timer.totalMs();
        long retrievalMs = retrieval.embeddingMs() + retrieval.searchMs();

        Message userMessage = Message.user(request.question());
        conversation.addMessage(userMessage);

        Message assistantMessage = Message.assistant(answer);
        assistantMessage.setModel(chatCompletionClient.modelName());
        assistantMessage.setRetrievalMs(retrievalMs);
        assistantMessage.setLlmMs(llmMs);
        assistantMessage.setTotalMs(totalMs);
        assistantMessage.setRetrievedChunks(context.includedChunks().size());
        assistantMessage.setTopSimilarity(retrieval.topSimilarity());
        attachCitations(assistantMessage, context.includedChunks());
        conversation.addMessage(assistantMessage);

        conversationRepository.saveAndFlush(conversation);

        log.info("Answered question in conversation {}: retrieval={}ms (embed={}ms search={}ms) llm={}ms total={}ms "
                        + "passages={} topSimilarity={}",
                conversation.getId(), retrievalMs, retrieval.embeddingMs(), retrieval.searchMs(), llmMs, totalMs,
                context.includedChunks().size(), String.format("%.3f", retrieval.topSimilarity()));
        auditService.record("CHAT_QUESTION", "Conversation", conversation.getId(), null);

        return new ChatResponse(
                conversation.getId(),
                conversation.getTitle(),
                assistantMessage.getId(),
                answer,
                toCitationResponses(context.includedChunks()),
                retrieval.confidenceLabel(),
                retrieval.topSimilarity(),
                context.includedChunks().size(),
                chatCompletionClient.modelName(),
                new ChatResponse.Timings(retrieval.embeddingMs(), retrieval.searchMs(), llmMs, totalMs),
                assistantMessage.getCreatedAt());
    }

    /**
     * "Ask this document": the same pipeline with retrieval restricted to one document.
     * The exchange is not stored in the conversation history because it is a lookup against
     * a single file rather than an ongoing thread.
     */
    @Transactional(readOnly = true)
    public ChatResponse askDocument(Long documentId, String question) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> ResourceNotFoundException.document(documentId));
        if (document.getStatus() != com.enterpriseai.hub.domain.DocumentStatus.READY) {
            throw new ApiException(ErrorCode.DOCUMENT_NOT_READY,
                    "Document " + documentId + " is " + document.getStatus() + " and cannot be queried yet");
        }

        StageTimer timer = StageTimer.start();
        RetrievalResult retrieval = documentRetriever.retrieve(question, List.of(documentId));
        ContextBuilder.BuiltContext context = contextBuilder.build(retrieval.chunks());

        StageTimer llmTimer = StageTimer.start();
        String answer = retrieval.isEmpty()
                ? chatCompletionClient.complete(PromptTemplates.NO_CONTEXT_SYSTEM_PROMPT, "QUESTION: " + question)
                : chatCompletionClient.complete(PromptTemplates.RAG_SYSTEM_PROMPT,
                PromptTemplates.RAG_USER_PROMPT_TEMPLATE.formatted(context.text(), question));
        long llmMs = llmTimer.totalMs();

        return new ChatResponse(
                null,
                document.getTitle(),
                null,
                answer,
                toCitationResponses(context.includedChunks()),
                retrieval.confidenceLabel(),
                retrieval.topSimilarity(),
                context.includedChunks().size(),
                chatCompletionClient.modelName(),
                new ChatResponse.Timings(retrieval.embeddingMs(), retrieval.searchMs(), llmMs, timer.totalMs()),
                java.time.Instant.now());
    }

    private Conversation resolveConversation(Long conversationId, Long userId, String firstQuestion) {
        if (conversationId != null) {
            return conversationRepository.findByIdAndUserId(conversationId, userId)
                    .orElseThrow(() -> ResourceNotFoundException.conversation(conversationId));
        }
        User user = userRepository.findById(userId).orElseThrow(() -> ResourceNotFoundException.user(userId));

        Conversation conversation = new Conversation();
        conversation.setUser(user);
        conversation.setTitle(deriveTitle(firstQuestion));
        return conversationRepository.save(conversation);
    }

    private void attachCitations(Message message, List<RetrievedChunk> chunks) {
        int rank = 1;
        for (RetrievedChunk chunk : chunks) {
            MessageCitation citation = new MessageCitation();
            citation.setDocument(documentRepository.getReferenceById(chunk.documentId()));
            citation.setChunkId(chunk.chunkId());
            citation.setPageNumber(chunk.pageNumber());
            citation.setSection(chunk.section());
            citation.setExcerpt(chunk.excerpt(EXCERPT_LENGTH));
            citation.setSimilarity(chunk.similarity());
            citation.setCitationRank(rank++);
            message.addCitation(citation);
        }
    }

    private List<CitationResponse> toCitationResponses(List<RetrievedChunk> chunks) {
        if (chunks.isEmpty()) {
            return Collections.emptyList();
        }
        List<CitationResponse> citations = new ArrayList<>(chunks.size());
        int rank = 1;
        for (RetrievedChunk chunk : chunks) {
            citations.add(new CitationResponse(
                    rank++,
                    chunk.documentId(),
                    chunk.documentTitle(),
                    chunk.fileName(),
                    chunk.chunkId(),
                    chunk.pageNumber(),
                    chunk.section(),
                    chunk.excerpt(EXCERPT_LENGTH),
                    chunk.similarity()));
        }
        return citations;
    }

    /**
     * Derives a conversation title locally from the first question. Asking the model for a
     * title would add a second round trip to the very first answer the user waits for; the
     * dedicated "suggest title" endpoint exists for when a better title is actually wanted.
     */
    private String deriveTitle(String question) {
        String normalized = question.replaceAll("\\s+", " ").strip();
        if (normalized.length() <= TITLE_MAX_LENGTH) {
            return normalized;
        }
        String truncated = normalized.substring(0, TITLE_MAX_LENGTH);
        int lastSpace = truncated.lastIndexOf(' ');
        return (lastSpace > 20 ? truncated.substring(0, lastSpace) : truncated) + "...";
    }

    /**
     * Renders the last few turns of an existing conversation so follow-up questions such as
     * "and what about the corporate account?" can be resolved.
     *
     * <p>The window is bounded ({@code app.ai.max-conversation-history-messages}) and each
     * turn is truncated. An unbounded history would grow the prompt without limit, push the
     * retrieved passages out of the context window, and make every later turn slower and
     * more expensive than the last.</p>
     */
    private String recentHistoryBlock(Conversation conversation) {
        if (!properties.getRag().isIncludeConversationHistory() || conversation.getId() == null) {
            return "";
        }
        int window = properties.getAi().getMaxConversationHistoryMessages();
        if (window <= 0) {
            return "";
        }

        List<Message> recent = new ArrayList<>(messageRepository.findByConversationIdOrderByCreatedAtDescIdDesc(
                conversation.getId(), org.springframework.data.domain.PageRequest.of(0, window)));
        if (recent.isEmpty()) {
            return "";
        }
        Collections.reverse(recent);

        StringBuilder history = new StringBuilder("PREVIOUS TURNS (for pronoun and follow-up resolution only; "
                + "do not treat these as sources):\n");
        for (Message message : recent) {
            history.append(message.getRole() == MessageRole.USER ? "User: " : "Assistant: ")
                    .append(truncateTurn(message.getContent()))
                    .append('\n');
        }
        return history.append('\n').toString();
    }

    private String truncateTurn(String content) {
        String normalized = content.replaceAll("\\s+", " ").strip();
        return normalized.length() <= 400 ? normalized : normalized.substring(0, 400) + "...";
    }
}
