package com.enterpriseai.hub.document;

import com.enterpriseai.hub.exception.ApiException;
import com.enterpriseai.hub.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.StringJoiner;

/**
 * DOCX extraction via Apache POI.
 *
 * <p>Word documents have no fixed pagination (it depends on the rendering engine), so the
 * whole body is reported as page 1 and citations fall back to the section heading. Tables
 * are flattened row by row because enterprise documents keep limits, fees and SLAs in
 * tables, and losing them would make a large share of questions unanswerable.</p>
 */
@Slf4j
@Component
@Order(20)
public class DocxTextExtractor implements TextExtractor {

    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @Override
    public boolean supports(String contentType, String fileExtension) {
        return DOCX_CONTENT_TYPE.equalsIgnoreCase(contentType) || "docx".equalsIgnoreCase(fileExtension);
    }

    @Override
    public ExtractedText extract(Path file) {
        try (InputStream in = Files.newInputStream(file);
             XWPFDocument document = new XWPFDocument(in)) {

            StringBuilder body = new StringBuilder();

            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText();
                if (text != null && !text.isBlank()) {
                    body.append(text.strip()).append("\n\n");
                }
            }

            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    StringJoiner cells = new StringJoiner(" | ");
                    for (XWPFTableCell cell : row.getTableCells()) {
                        String cellText = cell.getText();
                        cells.add(cellText == null ? "" : cellText.strip());
                    }
                    String rowText = cells.toString().strip();
                    if (!rowText.replace("|", "").isBlank()) {
                        body.append(rowText).append("\n");
                    }
                }
                body.append("\n");
            }

            String text = body.toString().strip();
            if (text.isEmpty()) {
                return new ExtractedText(List.of(), 1, 0);
            }
            log.debug("Extracted {} characters from DOCX", text.length());
            return new ExtractedText(List.of(new ExtractedPage(1, text)), 1, text.length());
        } catch (IOException | RuntimeException ex) {
            throw new ApiException(ErrorCode.TEXT_EXTRACTION_FAILED,
                    "The Word document could not be read. Only .docx (Office Open XML) is supported.", ex);
        }
    }
}
