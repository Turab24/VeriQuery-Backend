package com.enterpriseai.hub.repository;

import com.enterpriseai.hub.domain.DocumentChunk;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    List<DocumentChunk> findByDocumentIdOrderByChunkIndexAsc(Long documentId);

    /** First N chunks in document order - used for previews and for AI analysis extracts. */
    List<DocumentChunk> findByDocumentIdOrderByChunkIndexAsc(Long documentId, Pageable pageable);

    long countByDocumentId(Long documentId);

    @Modifying
    @Query("DELETE FROM DocumentChunk c WHERE c.document.id = :documentId")
    int deleteByDocumentId(@Param("documentId") Long documentId);
}
