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

/**
 * Provenance for an assistant answer: which document, which page and which passage the
 * model was given as context, together with the similarity score that surfaced it.
 */
@Entity
@Table(name = "message_citations")
@Getter
@Setter
@NoArgsConstructor
public class MessageCitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "chunk_id")
    private Long chunkId;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(name = "section", length = 255)
    private String section;

    @Column(name = "excerpt", columnDefinition = "text")
    private String excerpt;

    @Column(name = "similarity", nullable = false)
    private double similarity;

    @Column(name = "citation_rank", nullable = false)
    private int citationRank;
}
