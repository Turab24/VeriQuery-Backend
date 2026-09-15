package com.enterpriseai.hub.dto.conversation;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "Conversation")
public record ConversationResponse(
        Long id,
        String title,
        int messageCount,
        boolean archived,
        Instant createdAt,
        Instant updatedAt) {
}
