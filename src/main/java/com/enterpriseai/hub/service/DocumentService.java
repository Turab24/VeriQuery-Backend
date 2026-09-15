package com.enterpriseai.hub.service;

import com.enterpriseai.hub.domain.Document;
import com.enterpriseai.hub.domain.DocumentCategory;
import com.enterpriseai.hub.domain.DocumentChunk;
import com.enterpriseai.hub.domain.DocumentStatus;
import com.enterpriseai.hub.domain.User;
import com.enterpriseai.hub.dto.document.DocumentDetailResponse;
import com.enterpriseai.hub.dto.document.DocumentResponse;
import com.enterpriseai.hub.dto.document.UpdateDocumentRequest;
import com.enterpriseai.hub.exception.ApiException;
import com.enterpriseai.hub.exception.BadRequestException;
import com.enterpriseai.hub.exception.ErrorCode;
import com.enterpriseai.hub.exception.ResourceNotFoundException;
import com.enterpriseai.hub.mapper.DocumentMapper;
import com.enterpriseai.hub.repository.DocumentChunkRepository;
import com.enterpriseai.hub.repository.DocumentRepository;
import com.enterpriseai.hub.repository.DocumentSpecifications;
import com.enterpriseai.hub.repository.UserRepository;
import com.enterpriseai.hub.repository.VectorSearchRepository;
import com.enterpriseai.hub.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final int CHUNK_PREVIEW_LIMIT = 8;

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final VectorSearchRepository vectorSearchRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final DocumentProcessingService documentProcessingService;
    private final DocumentMapper documentMapper;
    private final AuditService auditService;

    /**
     * Accepts an upload, persists its metadata and hands the heavy work to the ingestion
     * pool. The response returns immediately with status {@code PENDING}; the client polls
     * or refreshes to watch the document move through {@code PROCESSING} to {@code READY}.
     *
     * <p>The async job is scheduled <i>after</i> the transaction commits. Scheduling it
     * inline would let the worker start before the row is visible to other connections and
     * fail with a spurious "document not found".</p>
     */
    @Transactional
    public DocumentResponse upload(MultipartFile file, String providedTitle) {
        StorageService.StoredFile stored = storageService.store(file);

        Optional<Document> duplicate = documentRepository.findByChecksum(stored.checksum());
        if (duplicate.isPresent()) {
            storageService.delete(stored.relativePath());
            throw new ApiException(ErrorCode.DUPLICATE_DOCUMENT,
                    "This file has already been uploaded as '" + duplicate.get().getTitle()
                            + "' (document " + duplicate.get().getId() + ")");
        }

        User uploader = userRepository.findById(SecurityUtils.currentUserId())
                .orElseThrow(() -> ResourceNotFoundException.user(SecurityUtils.currentUserId()));

        Document document = new Document();
        document.setTitle(resolveTitle(providedTitle, file.getOriginalFilename()));
        document.setFileName(file.getOriginalFilename());
        document.setContentType(file.getContentType());
        document.setFileSize(stored.size());
        document.setStoragePath(stored.relativePath());
        document.setChecksum(stored.checksum());
        document.setStatus(DocumentStatus.PENDING);
        document.setUploadedBy(uploader);

        Document saved = documentRepository.save(document);
        log.info("Accepted upload '{}' as document {} ({} bytes)", saved.getFileName(), saved.getId(), stored.size());
        auditService.record("DOCUMENT_UPLOADED", "Document", saved.getId(), saved.getFileName());

        scheduleAfterCommit(saved.getId());
        return documentMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<DocumentResponse> list(String status, String search, Pageable pageable) {
        Specification<Document> specification =
                DocumentSpecifications.filter(parseStatus(status), emptyToNull(search));
        return documentRepository.findAll(specification, pageable).map(documentMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public DocumentDetailResponse getDetail(Long id) {
        Document document = documentRepository.findWithUploaderById(id)
                .orElseThrow(() -> ResourceNotFoundException.document(id));
        List<DocumentChunk> previews = documentChunkRepository
                .findByDocumentIdOrderByChunkIndexAsc(id, PageRequest.of(0, CHUNK_PREVIEW_LIMIT));
        return documentMapper.toDetail(document, previews);
    }

    @Transactional(readOnly = true)
    public DocumentResponse get(Long id) {
        return documentRepository.findWithUploaderById(id)
                .map(documentMapper::toResponse)
                .orElseThrow(() -> ResourceNotFoundException.document(id));
    }

    @Transactional
    public DocumentResponse update(Long id, UpdateDocumentRequest request) {
        Document document = documentRepository.findWithUploaderById(id)
                .orElseThrow(() -> ResourceNotFoundException.document(id));
        document.setTitle(request.title().strip());
        if (StringUtils.hasText(request.category())) {
            document.setCategory(DocumentCategory.fromModelOutput(request.category()));
        }
        auditService.record("DOCUMENT_UPDATED", "Document", id, request.title());
        return documentMapper.toResponse(documentRepository.save(document));
    }

    /**
     * Removes the document, its chunks and vectors (via the FK cascade plus an explicit
     * vector delete) and the stored binary.
     */
    @Transactional
    public void delete(Long id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.document(id));
        String storagePath = document.getStoragePath();

        vectorSearchRepository.deleteByDocumentId(id);
        documentRepository.delete(document);
        storageService.delete(storagePath);

        log.info("Deleted document {} ({})", id, document.getFileName());
        auditService.record("DOCUMENT_DELETED", "Document", id, document.getFileName());
    }

    /** Re-runs the pipeline, for example after a transient embedding-provider outage. */
    @Transactional
    public DocumentResponse reprocess(Long id) {
        Document document = documentRepository.findWithUploaderById(id)
                .orElseThrow(() -> ResourceNotFoundException.document(id));

        if (document.getStatus() == DocumentStatus.PROCESSING) {
            throw new BadRequestException(ErrorCode.CONFLICT, "This document is already being processed");
        }
        if (!storageService.exists(document.getStoragePath())) {
            throw new ApiException(ErrorCode.STORAGE_ERROR,
                    "The stored file for this document is missing; upload it again");
        }

        document.setStatus(DocumentStatus.PENDING);
        document.setProcessingError(null);
        documentRepository.save(document);
        auditService.record("DOCUMENT_REPROCESS", "Document", id, null);

        scheduleAfterCommit(id);
        return documentMapper.toResponse(document);
    }

    @Transactional(readOnly = true)
    public DownloadableDocument prepareDownload(Long id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.document(id));
        Path path = storageService.resolve(document.getStoragePath());
        if (!path.toFile().exists()) {
            throw new ApiException(ErrorCode.STORAGE_ERROR, "The stored file for this document is missing");
        }
        return new DownloadableDocument(path, document.getFileName(), document.getContentType());
    }

    private void scheduleAfterCommit(Long documentId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    documentProcessingService.processAsync(documentId);
                }
            });
        } else {
            documentProcessingService.processAsync(documentId);
        }
    }

    private String resolveTitle(String providedTitle, String fileName) {
        if (StringUtils.hasText(providedTitle)) {
            return providedTitle.strip().length() > 255 ? providedTitle.strip().substring(0, 255) : providedTitle.strip();
        }
        if (!StringUtils.hasText(fileName)) {
            return "Untitled document";
        }
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String cleaned = base.replace('_', ' ').replace('-', ' ').strip();
        return cleaned.isEmpty() ? fileName : cleaned;
    }

    private DocumentStatus parseStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        try {
            return DocumentStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unknown document status: " + status);
        }
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value.strip() : null;
    }

    public record DownloadableDocument(Path path, String fileName, String contentType) {
    }
}
