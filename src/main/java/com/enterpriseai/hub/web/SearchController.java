package com.enterpriseai.hub.web;

import com.enterpriseai.hub.dto.search.SearchResultResponse;
import com.enterpriseai.hub.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Search", description = "Semantic search over the indexed knowledge base")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Validated
public class SearchController {

    private final SearchService searchService;

    @Operation(summary = "Semantic search",
            description = """
                    Matches on meaning rather than keywords: a query such as "how do I stop a cheque?"
                    retrieves a passage headed "Cheque Stop Payment Procedure" even though the two share
                    almost no literal terms. Returns passages with their document, page and similarity
                    score; no text is generated, so this is both faster and cheaper than /api/chat.
                    """)
    @GetMapping
    public ResponseEntity<SearchResultResponse> search(
            @RequestParam("q") @NotBlank @Size(max = 1000) String query,
            @RequestParam(required = false) @Min(1) @Max(25) Integer limit,
            @RequestParam(required = false) List<Long> documentIds) {
        return ResponseEntity.ok(searchService.search(query, limit, documentIds));
    }
}
