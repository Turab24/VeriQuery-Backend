package com.enterpriseai.hub.dto.conversation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "ConversationTitleRequest", description = "Create or rename a conversation")
public record ConversationTitleRequest(

        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Titles are limited to 200 characters")
        String title) {
}
