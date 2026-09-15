package com.enterpriseai.hub.service;

import com.enterpriseai.hub.ai.ChatCompletionClient;
import com.enterpriseai.hub.ai.PromptTemplates;
import com.enterpriseai.hub.domain.Document;
import com.enterpriseai.hub.domain.DocumentCategory;
import com.enterpriseai.hub.domain.DocumentChunk;
import com.enterpriseai.hub.domain.DocumentStatus;
import com.enterpriseai.hub.dto.document.DocumentFaqResponse;
import com.enterpriseai.hub.dto.document.DocumentSummaryResponse;
import com.enterpriseai.hub.exception.ApiException;
import com.enterpriseai.hub.exception.ErrorCode;
import com.enterpriseai.hub.exception.ResourceNotFoundException;
import com.enterpriseai.hub.observability.StageTimer;
import com.enterpriseai.hub.repository.DocumentChunkRepository;
import com.enterpriseai.hub.repository.DocumentRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Document-level AI features: summarisation, classification and FAQ generation.
 *
 * <p>All three follow the same discipline as the chat pipeline - the model is only ever
 * shown text taken from the document itself, never asked to recall anything.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentAiService {

    private static final int MAX_ANALYSIS_CHARACTERS = 12_000;
    private static final int DEFAULT_FAQ_COUNT = 5;

    private final ChatCompletionClient chatCompletionClient;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final ObjectMapper objectMapper;

    // ------------------------------------------------------------------ ingestion hooks

    /** Best-effort classification used during ingestion; never throws. */
    public DocumentCategory classifySafely(String title, String text) {
        try {
            String response = chatCompletionClient.complete(
                    PromptTemplates.CLASSIFICATION_SYSTEM_PROMPT,
                    "TITLE: %s\n\nEXTRACT:\n%s".formatted(title, truncate(text, 4_000)));
            DocumentCategory category = DocumentCategory.fromModelOutput(response);
            log.debug("Classified '{}' as {}", title, category);
            return category;
        } catch (RuntimeException ex) {
            log.warn("Classification skipped for '{}': {}", title, ex.getMessage());
            return DocumentCategory.OTHER;
        }
    }

    /** Best-effort summarisation used during ingestion; never throws. */
    public String summariseSafely(String title, String text) {
        try {
            return chatCompletionClient.complete(
                    PromptTemplates.SUMMARY_SYSTEM_PROMPT,
                    "TITLE: %s\n\nEXTRACT:\n%s".formatted(title, truncate(text, MAX_ANALYSIS_CHARACTERS)));
        } catch (RuntimeException ex) {
            log.warn("Summarisation skipped for '{}': {}", title, ex.getMessage());
            return null;
        }
    }

    // --------------------------------------------------------------------- API features

    /**
     * Returns the stored summary when one exists, otherwise generates and stores it.
     * Summaries are stable per document, so regenerating on every request would spend
     * tokens to produce the same paragraph.
     */
    @Transactional
    public DocumentSummaryResponse summarise(Long documentId, boolean forceRegenerate) {
        Document document = requireReadyDocument(documentId);

        if (!forceRegenerate && document.getSummary() != null && !document.getSummary().isBlank()) {
            return new DocumentSummaryResponse(document.getId(), document.getTitle(),
                    document.getSummary(), true, chatCompletionClient.modelName(), 0L);
        }

        StageTimer timer = StageTimer.start();
        String summary = chatCompletionClient.complete(
                PromptTemplates.SUMMARY_SYSTEM_PROMPT,
                "TITLE: %s\n\nEXTRACT:\n%s".formatted(document.getTitle(), documentExtract(documentId)));
        long elapsed = timer.totalMs();

        document.setSummary(summary);
        documentRepository.save(document);

        log.info("Generated summary for document {} in {}ms", documentId, elapsed);
        return new DocumentSummaryResponse(document.getId(), document.getTitle(), summary, false,
                chatCompletionClient.modelName(), elapsed);
    }

    @Transactional(readOnly = true)
    public DocumentFaqResponse generateFaq(Long documentId, Integer requestedCount) {
        Document document = requireReadyDocument(documentId);
        int count = requestedCount == null ? DEFAULT_FAQ_COUNT : Math.clamp(requestedCount, 1, 10);

        StageTimer timer = StageTimer.start();
        String response = chatCompletionClient.complete(
                PromptTemplates.FAQ_SYSTEM_PROMPT,
                "Generate exactly %d question/answer pairs.\n\nTITLE: %s\n\nEXTRACT:\n%s"
                        .formatted(count, document.getTitle(), documentExtract(documentId)));

        List<DocumentFaqResponse.FaqItem> items = parseFaq(response, count);
        long elapsed = timer.totalMs();

        log.info("Generated {} FAQ item(s) for document {} in {}ms", items.size(), documentId, elapsed);
        return new DocumentFaqResponse(document.getId(), document.getTitle(), items,
                chatCompletionClient.modelName(), elapsed);
    }

    // ------------------------------------------------------------------------- internals

    /**
     * Models return JSON with varying amounts of ceremony (markdown fences, a leading
     * sentence). Parsing is therefore defensive: locate the array, parse it, and fall back
     * to a line-based reading rather than failing the request outright.
     */
    private List<DocumentFaqResponse.FaqItem> parseFaq(String response, int expectedCount) {
        String json = response == null ? "" : response.strip();
        int start = json.indexOf('[');
        int end = json.lastIndexOf(']');
        if (start >= 0 && end > start) {
            json = json.substring(start, end + 1);
            try {
                List<Map<String, String>> parsed =
                        objectMapper.readValue(json, new TypeReference<List<Map<String, String>>>() {
                        });
                List<DocumentFaqResponse.FaqItem> items = new ArrayList<>();
                for (Map<String, String> entry : parsed) {
                    String question = entry.get("question");
                    String answer = entry.get("answer");
                    if (question != null && answer != null) {
                        items.add(new DocumentFaqResponse.FaqItem(question.strip(), answer.strip()));
                    }
                    if (items.size() >= expectedCount) {
                        break;
                    }
                }
                if (!items.isEmpty()) {
                    return items;
                }
            } catch (Exception ex) {
                log.warn("FAQ response was not valid JSON, falling back to text parsing: {}", ex.getMessage());
            }
        }
        return fallbackFaq(response, expectedCount);
    }

    private List<DocumentFaqResponse.FaqItem> fallbackFaq(String response, int expectedCount) {
        List<DocumentFaqResponse.FaqItem> items = new ArrayList<>();
        if (response == null || response.isBlank()) {
            return items;
        }
        String question = null;
        for (String line : response.split("\\R")) {
            String trimmed = line.strip().replaceFirst("^[-*\\d.\\s]+", "");
            if (trimmed.isEmpty()) {
                continue;
            }
            if (question == null && trimmed.endsWith("?")) {
                question = trimmed;
            } else if (question != null) {
                items.add(new DocumentFaqResponse.FaqItem(question, trimmed));
                question = null;
                if (items.size() >= expectedCount) {
                    break;
                }
            }
        }
        return items;
    }

    private String documentExtract(Long documentId) {
        List<DocumentChunk> chunks = documentChunkRepository
                .findByDocumentIdOrderByChunkIndexAsc(documentId, PageRequest.of(0, 12));
        StringBuilder builder = new StringBuilder();
        for (DocumentChunk chunk : chunks) {
            if (builder.length() >= MAX_ANALYSIS_CHARACTERS) {
                break;
            }
            builder.append(chunk.getContent()).append("\n\n");
        }
        return truncate(builder.toString(), MAX_ANALYSIS_CHARACTERS);
    }

    private Document requireReadyDocument(Long documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> ResourceNotFoundException.document(documentId));
        if (document.getStatus() != DocumentStatus.READY) {
            throw new ApiException(ErrorCode.DOCUMENT_NOT_READY,
                    "Document " + documentId + " is " + document.getStatus()
                            + ". AI features become available once processing completes.");
        }
        return document;
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
