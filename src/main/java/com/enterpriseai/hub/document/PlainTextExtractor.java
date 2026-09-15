package com.enterpriseai.hub.document;

import com.enterpriseai.hub.exception.ApiException;
import com.enterpriseai.hub.exception.ErrorCode;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Plain text and Markdown. Decoded as UTF-8 with replacement characters rather than
 * failing, so a file with a stray byte still yields a usable document.
 */
@Component
@Order(30)
public class PlainTextExtractor implements TextExtractor {

    @Override
    public boolean supports(String contentType, String fileExtension) {
        return (contentType != null && contentType.toLowerCase().startsWith("text/"))
                || "txt".equalsIgnoreCase(fileExtension)
                || "md".equalsIgnoreCase(fileExtension);
    }

    @Override
    public ExtractedText extract(Path file) {
        try {
            String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n")
                    .strip();
            if (text.isEmpty()) {
                return new ExtractedText(List.of(), 1, 0);
            }
            return new ExtractedText(List.of(new ExtractedPage(1, text)), 1, text.length());
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.TEXT_EXTRACTION_FAILED, "The text file could not be read.", ex);
        }
    }
}
