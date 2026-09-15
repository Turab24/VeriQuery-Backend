package com.enterpriseai.hub.document;

import com.enterpriseai.hub.exception.ApiException;
import com.enterpriseai.hub.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF extraction via PDFBox.
 *
 * <p>Pages are stripped one at a time rather than in a single pass. It is slightly slower,
 * but it is what allows a citation to say "page 12" instead of just naming the file - the
 * single most useful piece of provenance for a reader who has to verify an answer.</p>
 */
@Slf4j
@Component
@Order(10)
public class PdfTextExtractor implements TextExtractor {

    @Override
    public boolean supports(String contentType, String fileExtension) {
        return "application/pdf".equalsIgnoreCase(contentType) || "pdf".equalsIgnoreCase(fileExtension);
    }

    @Override
    public ExtractedText extract(Path file) {
        try (PDDocument document = Loader.loadPDF(file.toFile())) {
            int pageCount = document.getNumberOfPages();
            List<ExtractedPage> pages = new ArrayList<>(pageCount);

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            for (int pageNumber = 1; pageNumber <= pageCount; pageNumber++) {
                stripper.setStartPage(pageNumber);
                stripper.setEndPage(pageNumber);
                String text = normalize(stripper.getText(document));
                if (!text.isBlank()) {
                    pages.add(new ExtractedPage(pageNumber, text));
                }
            }

            int characters = pages.stream().mapToInt(page -> page.text().length()).sum();
            log.debug("Extracted {} characters from {} of {} PDF pages", characters, pages.size(), pageCount);
            return new ExtractedText(pages, pageCount, characters);
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.TEXT_EXTRACTION_FAILED,
                    "The PDF could not be read. It may be corrupt, encrypted or image-only.", ex);
        }
    }

    private String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        // PDFs are full of non-breaking spaces and soft hyphens; normalising them keeps
        // chunk boundaries and term matching predictable.
        return raw.replace("\r\n", "\n")
                .replace('\u00A0', ' ')
                .replace("\u00AD", "")
                .strip();
    }
}
