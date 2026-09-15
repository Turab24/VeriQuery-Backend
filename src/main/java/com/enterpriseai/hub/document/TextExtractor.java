package com.enterpriseai.hub.document;

import java.nio.file.Path;

/**
 * Strategy for turning a stored file into page-attributed plain text.
 * Adding a new supported format means adding one implementation - nothing else changes.
 */
public interface TextExtractor {

    boolean supports(String contentType, String fileExtension);

    ExtractedText extract(Path file);
}
