package com.enterpriseai.hub;

import com.enterpriseai.hub.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * EnterpriseAI Hub - AI-powered enterprise knowledge and support platform.
 *
 * <p>The application exposes a REST API that lets an organisation ingest internal
 * documentation, index it into a pgvector-backed vector store and answer natural
 * language questions against it using a Retrieval-Augmented Generation pipeline.</p>
 */
@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
@EnableCaching
@EnableAsync

@EnableScheduling
public class EnterpriseAiHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(EnterpriseAiHubApplication.class, args);
    }
}
