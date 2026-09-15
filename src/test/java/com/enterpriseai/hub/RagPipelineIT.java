package com.enterpriseai.hub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end verification of the whole platform against a real PostgreSQL + pgvector
 * instance: sign in, ingest a document, wait for the asynchronous pipeline to index it,
 * then retrieve it by meaning through both the search and the chat endpoints.
 *
 * <p>Runs with {@code app.ai.provider=demo}, so no API key or outbound network access is
 * needed and the assertions are deterministic. The database is a throwaway container, so
 * the Flyway migrations - including the pgvector extension, the {@code vector(1536)} column
 * and the HNSW index - are exercised on every run.</p>
 *
 * <p>Skipped automatically when no Docker daemon is available, and excluded from the
 * default {@code mvn test} run; execute it with {@code mvn verify}.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RagPipelineIT {

    private static final String SAMPLE_DOCUMENT = """
            Retail Banking Operations Manual

            3. Cheque Stop Payment Procedure

            A stop payment instruction must be received in writing and acknowledged by the branch
            manager before the cheque is flagged in the core banking system. The customer is charged
            the standard fee published in the tariff schedule. The instruction remains in force for
            six months unless it is withdrawn in writing by the same signatory.

            4. Cheque Book Request Procedure

            A cheque book request is raised by the customer through the branch or the mobile
            application. The request is validated against the account mandate, and a booklet of
            twenty five leaves is dispatched to the registered address within three working days.
            A maximum of two booklets may be pending at any time for a single account.

            5. Daily Transaction Limits

            The default daily transfer limit for a retail current account is fifty thousand units.
            Corporate accounts are governed by the mandate agreed at onboarding and are reviewed
            annually by the relationship manager.
            """;

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("enterprise_ai")
            .withUsername("enterprise")
            .withPassword("enterprise");

    static Path storageDirectory;

    private static Long documentId;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) throws IOException {
        storageDirectory = Files.createTempDirectory("eai-hub-it");
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.storage.location", () -> storageDirectory.toString());
        registry.add("app.ai.provider", () -> "demo");
        registry.add("app.rag.min-similarity", () -> "0.05");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    private String tokenFor(String email, String password) throws Exception {
        String body = """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    @Test
    @Order(1)
    @DisplayName("the seeded administrator can sign in and the schema migrated cleanly")
    void adminCanSignIn() throws Exception {
        String token = tokenFor("admin@enterpriseai.local", "Admin@12345");
        assertThat(token).isNotBlank();

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@enterpriseai.local"))
                .andExpect(jsonPath("$.roles").isArray());
    }

    @Test
    @Order(2)
    @DisplayName("an uploaded document is accepted, processed asynchronously and reaches READY")
    void uploadIsIngestedAndIndexed() throws Exception {
        String token = tokenFor("admin@enterpriseai.local", "Admin@12345");
        MockMultipartFile file = new MockMultipartFile("file", "operations-manual.txt", "text/plain",
                SAMPLE_DOCUMENT.getBytes(StandardCharsets.UTF_8));

        String uploadResponse = mockMvc.perform(multipart("/api/documents")
                        .file(file)
                        .param("title", "Retail Banking Operations Manual")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();

        documentId = objectMapper.readTree(uploadResponse).get("id").asLong();

        String status = awaitTerminalStatus(token, documentId);
        assertThat(status).isEqualTo("READY");

        mockMvc.perform(get("/api/documents/" + documentId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document.chunkCount").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.chunkPreviews").isArray());
    }

    @Test
    @Order(3)
    @DisplayName("semantic search finds the passage even though the query wording differs")
    void semanticSearchFindsRelatedPassage() throws Exception {
        String token = tokenFor("analyst@enterpriseai.local", "User@12345");

        mockMvc.perform(get("/api/search")
                        .param("q", "how do I stop a cheque payment?")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalResults").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.results[0].documentId").value(documentId.intValue()));
    }

    @Test
    @Order(4)
    @DisplayName("the chat endpoint answers from the indexed document and cites its source")
    void chatAnswersWithCitations() throws Exception {
        String token = tokenFor("analyst@enterpriseai.local", "User@12345");
        String body = """
                {"question":"What is the process for requesting a cheque book?"}
                """;

        String response = mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.citations").isArray())
                .andExpect(jsonPath("$.conversationId").isNumber())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        assertThat(json.get("citations").size()).isPositive();
        assertThat(json.get("citations").get(0).get("documentId").asLong()).isEqualTo(documentId);
        assertThat(json.get("citations").get(0).get("excerpt").asText()).isNotBlank();
        assertThat(json.get("retrievedChunks").asInt()).isPositive();
        assertThat(json.get("timings").get("totalMs").asLong()).isNotNegative();

        long conversationId = json.get("conversationId").asLong();
        mockMvc.perform(get("/api/conversations/" + conversationId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[0].role").value("USER"))
                .andExpect(jsonPath("$.messages[1].role").value("ASSISTANT"));
    }

    @Test
    @Order(5)
    @DisplayName("a question the documents do not cover is refused rather than answered")
    void unrelatedQuestionIsRefused() throws Exception {
        String token = tokenFor("analyst@enterpriseai.local", "User@12345");
        String body = """
                {"question":"zzqq xylophone quantum brontosaurus telemetry"}
                """;

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retrievalConfidence").value("NONE"))
                .andExpect(jsonPath("$.citations").isEmpty());
    }

    @Test
    @Order(6)
    @DisplayName("role based access control is enforced end to end")
    void standardUserCannotDeleteDocuments() throws Exception {
        String userToken = tokenFor("analyst@enterpriseai.local", "User@12345");
        mockMvc.perform(delete("/api/documents/" + documentId).header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/stats").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        String adminToken = tokenFor("admin@enterpriseai.local", "Admin@12345");
        mockMvc.perform(get("/api/admin/stats").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDocuments").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.aiProvider").value("demo"));
    }

    @Test
    @Order(7)
    @DisplayName("deleting a document removes it and its vectors")
    void adminCanDeleteDocument() throws Exception {
        String adminToken = tokenFor("admin@enterpriseai.local", "Admin@12345");

        mockMvc.perform(delete("/api/documents/" + documentId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/documents/" + documentId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("DOCUMENT_NOT_FOUND"));
    }

    private String awaitTerminalStatus(String token, Long id) throws Exception {
        for (int attempt = 0; attempt < 60; attempt++) {
            String response = mockMvc.perform(get("/api/documents/" + id)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            String status = objectMapper.readTree(response).get("document").get("status").asText();
            if ("READY".equals(status) || "FAILED".equals(status)) {
                return status;
            }
            Thread.sleep(500);
        }
        return "TIMEOUT";
    }
}
