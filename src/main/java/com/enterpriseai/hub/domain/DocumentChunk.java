package com.enterpriseai.hub.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A retrievable passage of a document.
 *
 * <p>The {@code embedding vector(1536)} column is intentionally <b>not</b> mapped here:
 * Hibernate has no native pgvector type, and loading embeddings through JPA would pull
 * tens of kilobytes of floats into memory for every listing query. Vector writes and
 * similarity searches go through
 * {@link com.enterpriseai.hub.repository.VectorSearchRepository} using plain JDBC, which
 * keeps the ORM model clean and the hot path allocation-free.</p>
 */
@Entity
@Table(name = "document_chunks")
@Getter
@Setter
@NoArgsConstructor
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "character_count", nullable = false)
    private int characterCount;

    @Column(name = "token_estimate", nullable = false)
    private int tokenEstimate;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(name = "section", length = 255)
    private String section;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}
