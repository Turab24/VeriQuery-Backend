package com.enterpriseai.hub.document;

import com.enterpriseai.hub.exception.ApiException;
import com.enterpriseai.hub.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

/**
 * Resolves the right {@link TextExtractor} for a file. Extractors are injected as an
 * ordered list, so registering a new format is a matter of adding a {@code @Component}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TextExtractionService {

    private final List<TextExtractor> extractors;

    public ExtractedText extract(Path file, String contentType, String fileName) {
        String extension = extensionOf(fileName);
        TextExtractor extractor = extractors.stream()
                .filter(candidate -> candidate.supports(contentType, extension))
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.UNSUPPORTED_FILE_TYPE,
                        "No text extractor is registered for content type '" + contentType + "'"));

        log.debug("Extracting {} with {}", fileName, extractor.getClass().getSimpleName());
        ExtractedText extracted = extractor.extract(file);

        if (extracted.isEmpty()) {
            throw new ApiException(ErrorCode.TEXT_EXTRACTION_FAILED,
                    "No machine-readable text was found in this file. Scanned or image-only documents "
                            + "need to be run through OCR before they can be indexed.");
        }
        return extracted;
    }

    public static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 && dot < fileName.length() - 1 ? fileName.substring(dot + 1).toLowerCase() : "";
    }
}
