package com.enterpriseai.hub.web;

import com.enterpriseai.hub.config.AppProperties;
import com.enterpriseai.hub.dto.document.DocumentResponse;
import com.enterpriseai.hub.exception.ApiException;
import com.enterpriseai.hub.exception.ErrorCode;
import com.enterpriseai.hub.exception.ResourceNotFoundException;
import com.enterpriseai.hub.security.JwtService;
import com.enterpriseai.hub.security.RestAccessDeniedHandler;
import com.enterpriseai.hub.security.RestAuthenticationEntryPoint;
import com.enterpriseai.hub.security.SecurityConfig;
import com.enterpriseai.hub.service.ChatService;
import com.enterpriseai.hub.service.DocumentAiService;
import com.enterpriseai.hub.service.DocumentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
@EnableConfigurationProperties(AppProperties.class)
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentService documentService;
    @MockitoBean
    private DocumentAiService documentAiService;
    @MockitoBean
    private ChatService chatService;
    @MockitoBean
    private JwtService jwtService;

    private DocumentResponse sampleDocument() {
        return new DocumentResponse(1L, "Cheque Procedures", "cheques.pdf", "application/pdf", 2048,
                "READY", "BANKING", 14, 32, 48_000, null, 1200L, "Admin", 1L, Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("listing documents requires authentication")
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("an authenticated user can list documents")
    void listReturnsPage() throws Exception {
        Page<DocumentResponse> page = new PageImpl<>(List.of(sampleDocument()), PageRequest.of(0, 10), 1);
        when(documentService.list(any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Cheque Procedures"))
                .andExpect(jsonPath("$.content[0].status").value("READY"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("a standard user may not delete documents")
    void deleteIsForbiddenForStandardUsers() throws Exception {
        mockMvc.perform(delete("/api/documents/1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACCESS_DENIED"));

        verify(documentService, never()).delete(anyLong());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("an administrator may delete documents")
    void deleteIsAllowedForAdministrators() throws Exception {
        mockMvc.perform(delete("/api/documents/1"))
                .andExpect(status().isNoContent());

        verify(documentService).delete(1L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("deleting a missing document returns 404 DOCUMENT_NOT_FOUND")
    void deleteMissingDocumentReturnsNotFound() throws Exception {
        doThrow(ResourceNotFoundException.document(42L)).when(documentService).delete(42L);

        mockMvc.perform(delete("/api/documents/42"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("DOCUMENT_NOT_FOUND"));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("an accepted upload returns 202 with the PENDING document")
    void uploadReturnsAccepted() throws Exception {
        when(documentService.upload(any(), any())).thenReturn(sampleDocument());
        MockMultipartFile file = new MockMultipartFile("file", "cheques.pdf", "application/pdf", "%PDF-".getBytes());

        mockMvc.perform(multipart("/api/documents").file(file))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("an unsupported file type returns 415 with a stable error code")
    void uploadRejectsUnsupportedType() throws Exception {
        when(documentService.upload(any(), any()))
                .thenThrow(new ApiException(ErrorCode.UNSUPPORTED_FILE_TYPE, "Unsupported file type"));
        MockMultipartFile file = new MockMultipartFile("file", "payload.exe", "application/octet-stream", "MZ".getBytes());

        mockMvc.perform(multipart("/api/documents").file(file))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error").value("UNSUPPORTED_FILE_TYPE"));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("asking about a document that is still processing returns 409")
    void askOnUnprocessedDocumentReturnsConflict() throws Exception {
        when(documentAiService.summarise(anyLong(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenThrow(new ApiException(ErrorCode.DOCUMENT_NOT_READY, "Document 1 is PROCESSING"));

        mockMvc.perform(get("/api/documents/1/summary"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DOCUMENT_NOT_READY"));
    }
}
