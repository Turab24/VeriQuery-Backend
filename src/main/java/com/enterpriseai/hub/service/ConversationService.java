package com.enterpriseai.hub.service;

import com.enterpriseai.hub.ai.ChatCompletionClient;
import com.enterpriseai.hub.ai.PromptTemplates;
import com.enterpriseai.hub.domain.Conversation;
import com.enterpriseai.hub.domain.Message;
import com.enterpriseai.hub.domain.MessageRole;
import com.enterpriseai.hub.domain.User;
import com.enterpriseai.hub.dto.conversation.ConversationDetailResponse;
import com.enterpriseai.hub.dto.conversation.ConversationResponse;
import com.enterpriseai.hub.exception.ResourceNotFoundException;
import com.enterpriseai.hub.mapper.ConversationMapper;
import com.enterpriseai.hub.repository.ConversationRepository;
import com.enterpriseai.hub.repository.MessageRepository;
import com.enterpriseai.hub.repository.UserRepository;
import com.enterpriseai.hub.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Conversation history.
 *
 * <p>Every lookup goes through {@code findByIdAndUserId}. Ownership is enforced by the
 * query itself rather than by a check after loading, so there is no code path where a
 * conversation belonging to another user can be read, renamed or deleted - and a probe for
 * someone else's id is indistinguishable from a request for one that does not exist.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ConversationMapper conversationMapper;
    private final ChatCompletionClient chatCompletionClient;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Page<ConversationResponse> list(String search, Pageable pageable) {
        Long userId = SecurityUtils.currentUserId();
        String term = StringUtils.hasText(search) ? search.strip() : null;
        Page<Conversation> conversations = term == null
                ? conversationRepository.findByUserId(userId, pageable)
                : conversationRepository.findByUserIdAndTitleContainingIgnoreCase(userId, term, pageable);
        return conversations.map(conversationMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public ConversationDetailResponse get(Long id) {
        Conversation conversation = requireOwned(id);
        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(id);
        return conversationMapper.toDetail(conversation, messages);
    }

    @Transactional
    public ConversationResponse create(String title) {
        Long userId = SecurityUtils.currentUserId();
        User user = userRepository.findById(userId).orElseThrow(() -> ResourceNotFoundException.user(userId));

        Conversation conversation = new Conversation();
        conversation.setUser(user);
        conversation.setTitle(title.strip());

        Conversation saved = conversationRepository.save(conversation);
        log.debug("Created conversation {} for user {}", saved.getId(), userId);
        return conversationMapper.toResponse(saved);
    }

    @Transactional
    public ConversationResponse rename(Long id, String title) {
        Conversation conversation = requireOwned(id);
        conversation.setTitle(title.strip());
        return conversationMapper.toResponse(conversationRepository.save(conversation));
    }

    @Transactional
    public ConversationResponse setArchived(Long id, boolean archived) {
        Conversation conversation = requireOwned(id);
        conversation.setArchived(archived);
        return conversationMapper.toResponse(conversationRepository.save(conversation));
    }

    @Transactional
    public void delete(Long id) {
        Conversation conversation = requireOwned(id);
        conversationRepository.delete(conversation);
        log.info("Deleted conversation {}", id);
        auditService.record("CONVERSATION_DELETED", "Conversation", id, null);
    }

    /**
     * Asks the model for a concise title based on the conversation's first question.
     * Offered on demand rather than automatically, so the first answer is never delayed by
     * a second model call.
     */
    @Transactional
    public ConversationResponse suggestTitle(Long id) {
        Conversation conversation = requireOwned(id);
        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(id);

        String firstQuestion = messages.stream()
                .filter(message -> message.getRole() == MessageRole.USER)
                .map(Message::getContent)
                .findFirst()
                .orElse(null);

        if (firstQuestion == null) {
            return conversationMapper.toResponse(conversation);
        }

        try {
            String suggestion = chatCompletionClient.complete(PromptTemplates.TITLE_SYSTEM_PROMPT, firstQuestion);
            String cleaned = suggestion.replaceAll("[\"'\\n\\r]", " ").replaceAll("\\s+", " ").strip();
            if (!cleaned.isEmpty()) {
                conversation.setTitle(cleaned.length() > 200 ? cleaned.substring(0, 200) : cleaned);
                conversationRepository.save(conversation);
            }
        } catch (RuntimeException ex) {
            log.warn("Title suggestion failed for conversation {}: {}", id, ex.getMessage());
        }
        return conversationMapper.toResponse(conversation);
    }

    private Conversation requireOwned(Long id) {
        return conversationRepository.findByIdAndUserId(id, SecurityUtils.currentUserId())
                .orElseThrow(() -> ResourceNotFoundException.conversation(id));
    }
}
