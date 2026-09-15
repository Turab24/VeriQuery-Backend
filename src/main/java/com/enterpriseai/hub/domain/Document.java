package com.enterpriseai.hub.domain;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Objects;

/**
 * A source document in the organisation's knowledge base.
 *
 * <p>The binary payload lives on the configured storage volume; only metadata and the
 * derived chunks are kept in PostgreSQL.</p>
 */
@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 120)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;

    /** SHA-256 of the uploaded bytes; used to detect duplicate uploads. */
    @Column(name = "checksum", nullable = false, length = 64)
    private String checksum;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private DocumentStatus status = DocumentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 40)
    private DocumentCategory category;

    @Column(name = "summary", columnDefinition = "text")
    private String summary;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "character_count", nullable = false)
    private int characterCount;

    @Column(name = "processing_error", columnDefinition = "text")
    private String processingError;

    @Column(name = "processing_ms")
    private Long processingMs;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void markProcessing() {
        this.status = DocumentStatus.PROCESSING;
        this.processingError = null;
    }

    public void markReady(int chunks, int characters, Integer pages, long elapsedMs) {
        this.status = DocumentStatus.READY;
        this.chunkCount = chunks;
        this.characterCount = characters;
        this.pageCount = pages;
        this.processingMs = elapsedMs;
        this.processedAt = Instant.now();
        this.processingError = null;
    }

    public void markFailed(String error) {
        this.status = DocumentStatus.FAILED;
        this.processingError = error;
        this.processedAt = Instant.now();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Document document)) {
            return false;
        }
        return id != null && id.equals(document.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
