package com.enterpriseai.hub.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "DashboardStats", description = "Aggregated platform statistics")
public record DashboardStatsResponse(
        long totalUsers,
        long activeUsers,
        long totalDocuments,
        long documentsReady,
        long documentsProcessing,
        long documentsPending,
        long documentsFailed,
        long totalChunks,
        long totalStorageBytes,
        long totalConversations,
        long totalQuestionsAsked,
        long questionsLast7Days,
        long documentsLast7Days,
        @Schema(description = "Mean end-to-end latency of assistant answers, in milliseconds. "
                + "Measured from this deployment's own request history; null until at least one answer exists.")
        Double averageAnswerLatencyMs,
        String aiProvider,
        String chatModel,
        String embeddingModel,
        List<CategoryCount> documentsByCategory,
        List<StatusCount> documentsByStatus,
        List<ActivityEntry> recentActivity) {

    @Schema(name = "CategoryCount")
    public record CategoryCount(String category, long count) {
    }

    @Schema(name = "StatusCount")
    public record StatusCount(String status, long count) {
    }

    @Schema(name = "ActivityEntry")
    public record ActivityEntry(
            Long id,
            String action,
            String actorEmail,
            String resourceType,
            String resourceId,
            String details,
            java.time.Instant createdAt) {
    }
}
