package com.enterpriseai.hub.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Thread pool for document ingestion.
 *
 * <p>Ingestion is slow and bursty: text extraction is CPU bound, embedding is network
 * bound and rate limited by the provider. Running it on the request thread would make a
 * 60-page PDF upload look like a hung browser, so uploads return {@code 202 Accepted} with
 * a {@code PENDING} document and the work continues here.</p>
 *
 * <p>The pool is deliberately small and the queue bounded. An unbounded queue would hide
 * a backlog until the JVM ran out of memory; with {@link ThreadPoolExecutor.CallerRunsPolicy}
 * a saturated pipeline instead pushes back on the caller, which is visible and recoverable.</p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AsyncConfig implements AsyncConfigurer {

    public static final String DOCUMENT_EXECUTOR = "documentProcessingExecutor";

    private final AppProperties properties;

    @Bean(name = DOCUMENT_EXECUTOR)
    public Executor documentProcessingExecutor() {
        AppProperties.Processing config = properties.getProcessing();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(config.getCorePoolSize());
        executor.setMaxPoolSize(config.getMaxPoolSize());
        executor.setQueueCapacity(config.getQueueCapacity());
        executor.setThreadNamePrefix("doc-ingest-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        log.info("Document ingestion pool: core={} max={} queue={}",
                config.getCorePoolSize(), config.getMaxPoolSize(), config.getQueueCapacity());
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return documentProcessingExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new SimpleAsyncUncaughtExceptionHandler();
    }
}
