package com.enterpriseai.hub.web;

import com.enterpriseai.hub.common.PageResponse;
import com.enterpriseai.hub.dto.conversation.ConversationDetailResponse;
import com.enterpriseai.hub.dto.conversation.ConversationResponse;
import com.enterpriseai.hub.dto.conversation.ConversationTitleRequest;
import com.enterpriseai.hub.service.ConversationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Conversations", description = "Chat history owned by the signed-in user")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
@Validated
public class ConversationController {

    private final ConversationService conversationService;

    @Operation(summary = "List the caller's conversations")
    @GetMapping
    public ResponseEntity<PageResponse<ConversationResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(PageResponse.of(conversationService.list(search,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt")))));
    }

    @Operation(summary = "Get a conversation with its full message history and citations")
    @GetMapping("/{id}")
    public ResponseEntity<ConversationDetailResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(conversationService.get(id));
    }

    @Operation(summary = "Create an empty conversation")
    @PostMapping
    public ResponseEntity<ConversationResponse> create(@Valid @RequestBody ConversationTitleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(conversationService.create(request.title()));
    }

    @Operation(summary = "Rename a conversation")
    @PatchMapping("/{id}")
    public ResponseEntity<ConversationResponse> rename(@PathVariable Long id,
                                                       @Valid @RequestBody ConversationTitleRequest request) {
        return ResponseEntity.ok(conversationService.rename(id, request.title()));
    }

    @Operation(summary = "Archive or unarchive a conversation")
    @PatchMapping("/{id}/archived")
    public ResponseEntity<ConversationResponse> setArchived(@PathVariable Long id,
                                                            @RequestParam boolean archived) {
        return ResponseEntity.ok(conversationService.setArchived(id, archived));
    }

    @Operation(summary = "Ask the model for a better title",
            description = "Generates a short title from the conversation's first question and stores it.")
    @PostMapping("/{id}/suggest-title")
    public ResponseEntity<ConversationResponse> suggestTitle(@PathVariable Long id) {
        return ResponseEntity.ok(conversationService.suggestTitle(id));
    }

    @Operation(summary = "Delete a conversation and its messages")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        conversationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
