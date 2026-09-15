package com.enterpriseai.hub.service;

import com.enterpriseai.hub.config.AppProperties;
import com.enterpriseai.hub.exception.ApiException;
import com.enterpriseai.hub.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StorageServiceTest {

    @TempDir
    Path tempDir;

    private StorageService storageService;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.getStorage().setLocation(tempDir.toString());
        properties.getStorage().setMaxFileSizeBytes(1024);
        properties.getStorage().setAllowedExtensions(List.of("txt", "pdf"));
        properties.getStorage().setAllowedContentTypes(List.of("text/plain", "application/pdf"));

        storageService = new StorageService(properties);
        storageService.initialise();
    }

    @Test
    @DisplayName("an accepted upload is written under the storage root with a generated name")
    void storesAcceptedUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "procedures.txt", "text/plain",
                "Cheque stop payment procedure".getBytes(StandardCharsets.UTF_8));

        StorageService.StoredFile stored = storageService.store(file);

        assertThat(stored.checksum()).hasSize(64);
        assertThat(stored.relativePath()).endsWith(".txt");
        assertThat(stored.relativePath()).doesNotContain("procedures");
        Path written = storageService.resolve(stored.relativePath());
        assertThat(Files.readString(written)).isEqualTo("Cheque stop payment procedure");
    }

    @Test
    @DisplayName("identical content yields an identical checksum so duplicates can be detected")
    void checksumIsContentAddressable() {
        byte[] content = "identical".getBytes(StandardCharsets.UTF_8);
        StorageService.StoredFile first =
                storageService.store(new MockMultipartFile("file", "a.txt", "text/plain", content));
        StorageService.StoredFile second =
                storageService.store(new MockMultipartFile("file", "b.txt", "text/plain", content));

        assertThat(first.checksum()).isEqualTo(second.checksum());
        assertThat(first.relativePath()).isNotEqualTo(second.relativePath());
    }

    @Test
    @DisplayName("an empty upload is rejected")
    void rejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

        assertThatThrownBy(() -> storageService.store(file))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.EMPTY_FILE);
    }

    @Test
    @DisplayName("an oversized upload is rejected before anything is written")
    void rejectsOversizedFile() {
        MockMultipartFile file = new MockMultipartFile("file", "big.txt", "text/plain", new byte[2048]);

        assertThatThrownBy(() -> storageService.store(file))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FILE_TOO_LARGE);
    }

    @Test
    @DisplayName("an executable disguised with an allowed content type is still rejected")
    void rejectsDisallowedExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "payload.exe", "text/plain",
                "MZ".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> storageService.store(file))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.UNSUPPORTED_FILE_TYPE);
    }

    @Test
    @DisplayName("an allowed extension with a disallowed content type is rejected")
    void rejectsDisallowedContentType() {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "application/x-msdownload",
                "data".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> storageService.store(file))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.UNSUPPORTED_FILE_TYPE);
    }

    @Test
    @DisplayName("a traversal attempt cannot escape the storage root")
    void resolveRejectsTraversal() {
        assertThatThrownBy(() -> storageService.resolve("../../etc/passwd"))
                .isInstanceOf(ApiException.class);
    }
}
