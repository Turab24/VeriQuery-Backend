package com.enterpriseai.hub.service;

import com.enterpriseai.hub.config.AppProperties;
import com.enterpriseai.hub.document.TextExtractionService;
import com.enterpriseai.hub.exception.ApiException;
import com.enterpriseai.hub.exception.ErrorCode;
import com.enterpriseai.hub.exception.StorageException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * Binary storage for uploaded documents.
 *
 * <p>Files land in a date-partitioned directory under the configured volume with a
 * generated name; the original file name is metadata only. That removes an entire class
 * of problems at once: path traversal via {@code ../}, collisions between two people
 * uploading {@code policy.pdf}, and unsafe characters reaching the filesystem.</p>
 *
 * <p>The abstraction is deliberately narrow (store / load / delete) so a deployment that
 * outgrows a mounted volume can swap in S3 or Azure Blob without touching the services.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private final AppProperties properties;

    private Path root;

    @PostConstruct
    void initialise() {
        this.root = Paths.get(properties.getStorage().getLocation()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
            log.info("Document storage root: {}", root);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create the document storage directory at " + root, ex);
        }
    }

    /**
     * Validates and stores an upload.
     *
     * @return the stored file's path relative to the storage root, plus its SHA-256.
     */
    public StoredFile store(MultipartFile file) {
        validate(file);

        String extension = TextExtractionService.extensionOf(file.getOriginalFilename());
        String relativeDirectory = LocalDate.now().toString().replace('-', '/');
        String generatedName = UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);

        Path directory = root.resolve(relativeDirectory).normalize();
        if (!directory.startsWith(root)) {
            throw new StorageException(ErrorCode.STORAGE_ERROR, "Resolved storage path escaped the storage root");
        }

        try {
            Files.createDirectories(directory);
            Path target = directory.resolve(generatedName);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = file.getInputStream();
                 DigestInputStream digestStream = new DigestInputStream(in, digest)) {
                Files.copy(digestStream, target, StandardCopyOption.REPLACE_EXISTING);
            }

            String checksum = HexFormat.of().formatHex(digest.digest());
            String relativePath = relativeDirectory + "/" + generatedName;
            log.debug("Stored upload {} as {} ({} bytes)", file.getOriginalFilename(), relativePath, file.getSize());
            return new StoredFile(relativePath, checksum, file.getSize());
        } catch (IOException ex) {
            throw new StorageException("Failed to store the uploaded file", ex);
        } catch (NoSuchAlgorithmException ex) {
            throw new StorageException("SHA-256 is not available in this JVM", ex);
        }
    }

    public Path resolve(String relativePath) {
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new StorageException(ErrorCode.STORAGE_ERROR, "Resolved path escaped the storage root");
        }
        return resolved;
    }

    public boolean exists(String relativePath) {
        return Files.exists(resolve(relativePath));
    }

    public void delete(String relativePath) {
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (IOException ex) {
            // Losing the binary is not a reason to fail the user's delete request:
            // the metadata and vectors are already gone, so log and continue.
            log.warn("Could not delete stored file {}: {}", relativePath, ex.getMessage());
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.EMPTY_FILE, "The uploaded file is empty");
        }
        if (file.getSize() > properties.getStorage().getMaxFileSizeBytes()) {
            throw new ApiException(ErrorCode.FILE_TOO_LARGE,
                    "The file exceeds the maximum size of "
                            + (properties.getStorage().getMaxFileSizeBytes() / (1024 * 1024)) + " MB");
        }

        String originalName = file.getOriginalFilename();
        if (!StringUtils.hasText(originalName)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "The uploaded file has no name");
        }

        String extension = TextExtractionService.extensionOf(originalName);
        String contentType = file.getContentType() == null
                ? ""
                : file.getContentType().toLowerCase(Locale.ROOT).split(";")[0].strip();

        boolean extensionAllowed = properties.getStorage().getAllowedExtensions().contains(extension);
        boolean contentTypeAllowed = properties.getStorage().getAllowedContentTypes().contains(contentType);

        // Both signals must agree. Browsers lie about content types, and an extension alone
        // is trivially forged, so requiring both raises the bar without blocking real users.
        if (!extensionAllowed || !contentTypeAllowed) {
            throw new ApiException(ErrorCode.UNSUPPORTED_FILE_TYPE,
                    "Unsupported file type. Allowed extensions: "
                            + String.join(", ", properties.getStorage().getAllowedExtensions()));
        }
    }

    public record StoredFile(String relativePath, String checksum, long size) {
    }
}
