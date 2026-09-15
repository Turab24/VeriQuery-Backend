package com.enterpriseai.hub.web;

import com.enterpriseai.hub.common.ApiErrorResponse;
import com.enterpriseai.hub.dto.chat.ChatRequest;
import com.enterpriseai.hub.dto.chat.ChatResponse;
import com.enterpriseai.hub.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Chat", description = "Retrieval-augmented question answering")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @Operation(summary = "Ask a question against the knowledge base",
            description = """
                    Embeds the question, runs an approximate nearest neighbour search over pgvector,
                    supplies the highest scoring passages to the chat model as context and returns the
                    answer together with its citations and per-stage timings.

                    When nothing clears the similarity threshold the response says so instead of
                    answering from the model's general knowledge.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Answer generated"),
            @ApiResponse(responseCode = "404", description = "Conversation not found or not owned by the caller",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "The AI provider is unavailable",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<ChatResponse> ask(@Valid @RequestBody ChatRequest request) {
        return ResponseEntity.ok(chatService.ask(request));
    }
}
