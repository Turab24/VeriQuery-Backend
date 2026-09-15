package com.enterpriseai.hub.repository;

import com.enterpriseai.hub.domain.Message;
import com.enterpriseai.hub.domain.MessageRole;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByConversationIdOrderByCreatedAtAscIdAsc(Long conversationId);

    /** Most recent turns first; combine with a {@code Pageable} to bound the history window. */
    List<Message> findByConversationIdOrderByCreatedAtDescIdDesc(Long conversationId, Pageable pageable);

    long countByRole(MessageRole role);

    long countByRoleAndCreatedAtAfter(MessageRole role, Instant instant);

    @Query("""
            SELECT AVG(m.totalMs) FROM Message m
            WHERE m.role = com.enterpriseai.hub.domain.MessageRole.ASSISTANT
              AND m.totalMs IS NOT NULL
            """)
    Double averageAssistantLatencyMs();
}
