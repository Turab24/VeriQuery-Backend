package com.enterpriseai.hub.mapper;

import com.enterpriseai.hub.domain.Conversation;
import com.enterpriseai.hub.domain.Message;
import com.enterpriseai.hub.domain.MessageCitation;
import com.enterpriseai.hub.dto.chat.CitationResponse;
import com.enterpriseai.hub.dto.conversation.ConversationDetailResponse;
import com.enterpriseai.hub.dto.conversation.ConversationResponse;
import com.enterpriseai.hub.dto.conversation.MessageResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ConversationMapper {

    public ConversationResponse toResponse(Conversation conversation) {
        return new ConversationResponse(
                conversation.getId(),
                conversation.getTitle(),
                conversation.getMessageCount(),
                conversation.isArchived(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt());
    }

    public ConversationDetailResponse toDetail(Conversation conversation, List<Message> messages) {
        return new ConversationDetailResponse(
                toResponse(conversation),
                messages.stream().map(this::toMessageResponse).toList());
    }

    public MessageResponse toMessageResponse(Message message) {
        return new MessageResponse(
                message.getId(),
                message.getRole().name(),
                message.getContent(),
                message.getCitations().stream().map(this::toCitationResponse).toList(),
                message.getModel(),
                message.getRetrievalMs(),
                message.getLlmMs(),
                message.getTotalMs(),
                message.getTopSimilarity(),
                message.getCreatedAt());
    }

    public CitationResponse toCitationResponse(MessageCitation citation) {
        return new CitationResponse(
                citation.getCitationRank(),
                citation.getDocument().getId(),
                citation.getDocument().getTitle(),
                citation.getDocument().getFileName(),
                citation.getChunkId(),
                citation.getPageNumber(),
                citation.getSection(),
                citation.getExcerpt(),
                citation.getSimilarity());
    }
}
