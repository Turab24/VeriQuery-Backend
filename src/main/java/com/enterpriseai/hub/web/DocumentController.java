package com.enterpriseai.hub.web;

import com.enterpriseai.hub.common.ApiErrorResponse;
import com.enterpriseai.hub.common.PageResponse;
import com.enterpriseai.hub.dto.chat.AskDocumentRequest;
import com.enterpriseai.hub.dto.chat.ChatResponse;
import com.enterpriseai.hub.dto.document.DocumentDetailResponse;
import com.enterpriseai.hub.dto.document.DocumentFaqResponse;
import com.enterpriseai.hub.dto.document.DocumentResponse;
import com.enterpriseai.hub.dto.document.DocumentSummaryResponse;
import com.enterpriseai.hub.dto.document.UpdateDocumentRequest;
import com.enterpriseai.hub.service.ChatService;
import com.enterpriseai.hub.service.DocumentAiService;
import com.enterpriseai.hub.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Documents", description = "Knowledge base ingestion and document level AI features")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Validated
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentAiService documentAiService;
    private final ChatService chatService;

    @Operation(summary = "Upload a document",
            description = "Stores the file and queues it for ingestion. Returns immediately with status PENDING; "
                    + "poll the document until it reaches READY or FAILED.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Accepted for processing"),
            @ApiResponse(responseCode = "409", description = "An identical file is already indexed",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "413", description = "File exceeds the configured size limit",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "415", description = "Unsupported file type",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title) {
        DocumentResponse response = documentService.upload(file, title);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @Operation(summary = "List documents", description = "Paginated, filterable by status and free text.")
    @GetMapping
    public ResponseEntity<PageResponse<DocumentResponse>> list(
            @Parameter(description = "PENDING, PROCESSING, READY or FAILED")
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction) {
        Page<DocumentResponse> result =
                documentService.list(status, search, PageRequest.of(page, size, Sort.by(direction, sortBy)));
        return ResponseEntity.ok(PageResponse.of(result));
    }

    @Operation(summary = "Get a document with indexed chunk previews")
    @GetMapping("/{id}")
    public ResponseEntity<DocumentDetailResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(documentService.getDetail(id));
    }

    @Operation(summary = "Update a document's title or category")
    @PutMapping("/{id}")
    public ResponseEntity<DocumentResponse> update(@PathVariable Long id,
                                                   @Valid @RequestBody UpdateDocumentRequest request) {
        return ResponseEntity.ok(documentService.update(id, request));
    }

    @Operation(summary = "Delete a document",
            description = "Removes the metadata, the indexed chunks with their vectors and the stored file. "
                    + "Administrators only.")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        documentService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Re-run the ingestion pipeline", description = "Useful after a transient provider failure.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/reprocess")
    public ResponseEntity<DocumentResponse> reprocess(@PathVariable Long id) {
        return ResponseEntity.accepted().body(documentService.reprocess(id));
    }

    @Operation(summary = "Download the original file")
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        DocumentService.DownloadableDocument download = documentService.prepareDownload(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + download.fileName().replace("\"", "") + "\"")
                .contentType(MediaType.parseMediaType(
                        download.contentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : download.contentType()))
                .body(new FileSystemResource(download.path()));
    }

    @Operation(summary = "Summarise the document",
            description = "Returns the stored summary, or generates one when absent or when regenerate=true.")
    @GetMapping("/{id}/summary")
    public ResponseEntity<DocumentSummaryResponse> summary(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean regenerate) {
        return ResponseEntity.ok(documentAiService.summarise(id, regenerate));
    }

    @Operation(summary = "Generate an FAQ from the document")
    @GetMapping("/{id}/faq")
    public ResponseEntity<DocumentFaqResponse> faq(
            @PathVariable Long id,
            @RequestParam(required = false) @Min(1) @Max(10) Integer count) {
        return ResponseEntity.ok(documentAiService.generateFaq(id, count));
    }

    @Operation(summary = "Ask a question about this document",
            description = "Runs the RAG pipeline with retrieval restricted to this document.")
    @PostMapping("/{id}/ask")
    public ResponseEntity<ChatResponse> ask(@PathVariable Long id,
                                            @Valid @RequestBody AskDocumentRequest request) {
        return ResponseEntity.ok(chatService.askDocument(id, request.question()));
    }
}
