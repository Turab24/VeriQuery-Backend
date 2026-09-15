package com.enterpriseai.hub.service;

import com.enterpriseai.hub.config.AppProperties;
import com.enterpriseai.hub.domain.AuditLog;
import com.enterpriseai.hub.domain.DocumentCategory;
import com.enterpriseai.hub.domain.DocumentStatus;
import com.enterpriseai.hub.domain.MessageRole;
import com.enterpriseai.hub.dto.admin.DashboardStatsResponse;
import com.enterpriseai.hub.repository.AuditLogRepository;
import com.enterpriseai.hub.repository.ConversationRepository;
import com.enterpriseai.hub.repository.DocumentRepository;
import com.enterpriseai.hub.repository.MessageRepository;
import com.enterpriseai.hub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dashboard aggregates.
 *
 * <p>Every figure is a real count from this deployment's own tables - there are no
 * synthetic or illustrative numbers anywhere in this service. The result is cached for a
 * short window (see {@code spring.cache.caffeine.spec}) because the dashboard polls and
 * these are the most expensive read queries in the application.</p>
 */
@Service
@RequiredArgsConstructor
public class AdminStatsService {

    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final AuditLogRepository auditLogRepository;
    private final AppProperties properties;

    @Cacheable("dashboardStats")
    @Transactional(readOnly = true)
    public DashboardStatsResponse dashboard() {
        Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);

        long totalDocuments = documentRepository.count();
        long ready = documentRepository.countByStatus(DocumentStatus.READY);
        long processing = documentRepository.countByStatus(DocumentStatus.PROCESSING);
        long pending = documentRepository.countByStatus(DocumentStatus.PENDING);
        long failed = documentRepository.countByStatus(DocumentStatus.FAILED);

        Map<String, Long> byCategory = new HashMap<>();
        for (Object[] row : documentRepository.countGroupedByCategory()) {
            DocumentCategory category = (DocumentCategory) row[0];
            byCategory.put(category.name(), ((Number) row[1]).longValue());
        }
        List<DashboardStatsResponse.CategoryCount> categoryCounts = Arrays.stream(DocumentCategory.values())
                .map(category -> new DashboardStatsResponse.CategoryCount(
                        category.name(), byCategory.getOrDefault(category.name(), 0L)))
                .filter(entry -> entry.count() > 0)
                .toList();

        List<DashboardStatsResponse.StatusCount> statusCounts = List.of(
                new DashboardStatsResponse.StatusCount(DocumentStatus.READY.name(), ready),
                new DashboardStatsResponse.StatusCount(DocumentStatus.PROCESSING.name(), processing),
                new DashboardStatsResponse.StatusCount(DocumentStatus.PENDING.name(), pending),
                new DashboardStatsResponse.StatusCount(DocumentStatus.FAILED.name(), failed));

        List<DashboardStatsResponse.ActivityEntry> activity = new ArrayList<>();
        for (AuditLog entry : auditLogRepository.findTop20ByOrderByCreatedAtDesc()) {
            activity.add(new DashboardStatsResponse.ActivityEntry(
                    entry.getId(), entry.getAction(), entry.getActorEmail(),
                    entry.getResourceType(), entry.getResourceId(), entry.getDetails(), entry.getCreatedAt()));
        }

        Double averageLatency = messageRepository.averageAssistantLatencyMs();

        return new DashboardStatsResponse(
                userRepository.count(),
                userRepository.countByEnabledTrue(),
                totalDocuments,
                ready,
                processing,
                pending,
                failed,
                documentRepository.sumChunkCount(),
                documentRepository.sumFileSize(),
                conversationRepository.count(),
                messageRepository.countByRole(MessageRole.USER),
                messageRepository.countByRoleAndCreatedAtAfter(MessageRole.USER, sevenDaysAgo),
                documentRepository.countByCreatedAtAfter(sevenDaysAgo),
                averageLatency,
                properties.getAi().getProvider(),
                properties.getAi().getChatModel(),
                properties.getAi().getEmbeddingModel(),
                categoryCounts,
                statusCounts,
                activity);
    }
}
