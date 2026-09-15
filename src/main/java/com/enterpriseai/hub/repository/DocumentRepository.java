package com.enterpriseai.hub.repository;

import com.enterpriseai.hub.domain.Document;
import com.enterpriseai.hub.domain.DocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long>, JpaSpecificationExecutor<Document> {

    /**
     * Redeclared purely to attach the entity graph: every row of the documents table shows
     * the uploader's name, so fetching the association lazily would issue one extra query
     * per row (the classic N+1).
     */
    @Override
    @EntityGraph(attributePaths = "uploadedBy")
    Page<Document> findAll(Specification<Document> specification, Pageable pageable);

    @EntityGraph(attributePaths = "uploadedBy")
    Optional<Document> findWithUploaderById(Long id);

    Optional<Document> findByChecksum(String checksum);

    long countByStatus(DocumentStatus status);

    long countByCreatedAtAfter(Instant instant);

    List<Document> findTop5ByStatusOrderByCreatedAtDesc(DocumentStatus status);

    @Query("SELECT COALESCE(SUM(d.chunkCount), 0) FROM Document d")
    long sumChunkCount();

    @Query("SELECT COALESCE(SUM(d.fileSize), 0) FROM Document d")
    long sumFileSize();

    @Query("SELECT d.category, COUNT(d) FROM Document d WHERE d.category IS NOT NULL GROUP BY d.category")
    List<Object[]> countGroupedByCategory();

    @Query("SELECT d FROM Document d WHERE d.status = :status ORDER BY d.createdAt ASC")
    List<Document> findByStatusOrderByCreatedAt(@Param("status") DocumentStatus status);
}
