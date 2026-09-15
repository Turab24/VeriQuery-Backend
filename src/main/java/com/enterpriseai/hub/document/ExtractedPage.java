package com.enterpriseai.hub.document;

/**
 * Text of a single logical page. Page numbers are what make page-level citations
 * possible, so every extractor must attribute its output to a page even when the source
 * format has no real pagination (plain text and DOCX report page 1).
 */
public record ExtractedPage(int pageNumber, String text) {
}
