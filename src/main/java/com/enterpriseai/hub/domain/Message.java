package com.enterpriseai.hub.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A single turn in a conversation. Assistant messages additionally carry the timings of
 * the retrieval and generation phases so that latency can be analysed per request
 * without re-running the pipeline.
 */
@Entity
@Table(name = "messages")
@Getter
@Setter
@NoArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private MessageRole role;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "model", length = 100)
    private String model;

    @Column(name = "retrieval_ms")
    private Long retrievalMs;

    @Column(name = "llm_ms")
    private Long llmMs;

    @Column(name = "total_ms")
    private Long totalMs;

    @Column(name = "retrieved_chunks")
    private Integer retrievedChunks;

    @Column(name = "top_similarity")
    private Double topSimilarity;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("citationRank ASC")
    private List<MessageCitation> citations = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public static Message user(String content) {
        return create(MessageRole.USER, content);
    }

    public static Message assistant(String content) {
        return create(MessageRole.ASSISTANT, content);
    }

    private static Message create(MessageRole role, String content) {
        Message message = new Message();
        message.setRole(role);
        message.setContent(content);
        // Set eagerly so ordering within a single turn is stable and the value is available
        // before the entity is flushed; @PrePersist leaves a non-null value untouched.
        message.setCreatedAt(Instant.now());
        return message;
    }

    public void addCitation(MessageCitation citation) {
        citation.setMessage(this);
        this.citations.add(citation);
    }
}
