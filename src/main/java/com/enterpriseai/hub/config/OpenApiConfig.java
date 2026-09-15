package com.enterpriseai.hub.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private int serverPort;

    @Bean
    public OpenAPI enterpriseAiHubOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("EnterpriseAI Hub API")
                        .version("1.0.0")
                        .description("""
                                AI-powered enterprise knowledge and support platform.

                                **How to use this API**
                                1. `POST /api/auth/login` with one of the seeded accounts to obtain a token pair.
                                2. Click **Authorize** and paste the `accessToken` (no `Bearer ` prefix needed).
                                3. `POST /api/documents` to ingest a PDF, TXT, MD or DOCX file. The document is
                                   queued asynchronously and becomes queryable once its status is `READY`.
                                4. `POST /api/chat` to ask a question. The answer comes back with the passages it
                                   was grounded in, including document, page and similarity score.

                                **Notes on the AI layer.** Retrieval confidence reflects vector similarity between
                                the question and the retrieved passages. It indicates how well the knowledge base
                                covered the question - it is not a claim that the generated answer is factually
                                correct.
                                """)
                        .contact(new Contact().name("EnterpriseAI Hub"))
                        .license(new License().name("MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:" + serverPort).description("Local development"),
                        new Server().url("/").description("Current host")))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT access token issued by /api/auth/login")));
    }
}
