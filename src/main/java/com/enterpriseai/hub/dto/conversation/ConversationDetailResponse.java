package com.enterpriseai.hub.dto.conversation;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "ConversationDetail")
public record ConversationDetailResponse(
        ConversationResponse conversation,
        List<MessageResponse> messages) {
}
