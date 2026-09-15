package com.enterpriseai.hub.document;

import com.enterpriseai.hub.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Splits extracted text into overlapping, retrieval-sized passages.
 *
 * <p><b>Why this strategy.</b> Fixed-width splitting is simple but routinely cuts a
 * procedure in half, which produces chunks that retrieve well and answer badly. This
 * chunker instead:</p>
 * <ul>
 *   <li>respects paragraph boundaries and only falls back to sentence splitting for
 *       paragraphs that are longer than the target on their own;</li>
 *   <li>carries a configurable character overlap into the next chunk so a statement that
 *       straddles a boundary is fully present in at least one passage;</li>
 *   <li>tracks the page a chunk started on and the most recent heading above it, which is
 *       what the citation layer shows to the user;</li>
 *   <li>merges a too-small trailing remainder into the previous chunk rather than emitting
 *       a fragment that would pollute the index.</li>
 * </ul>
 *
 * <p>Target size is a trade-off: large chunks carry more context per retrieval but dilute
 * the embedding, small chunks match precisely but arrive without their surroundings.
 * ~1200 characters (roughly 300 tokens) is a practical middle ground for procedural
 * enterprise documentation, and it is configurable per deployment.</p>
 */
@Component
@RequiredArgsConstructor
public class TextChunker {

    private static final Pattern PARAGRAPH_BREAK = Pattern.compile("\\n\\s*\\n");
    private static final Pattern SENTENCE_BREAK = Pattern.compile("(?<=[.!?])\\s+");
    private static final int MAX_SECTION_LENGTH = 120;

    private final AppProperties properties;

    public List<TextChunk> chunk(ExtractedText extractedText) {
        AppProperties.Chunking config = properties.getChunking();
        List<TextChunk> chunks = new ArrayList<>();

        StringBuilder buffer = new StringBuilder();
        Integer bufferPage = null;
        String currentSection = null;
        String bufferSection = null;

        for (ExtractedPage page : extractedText.pages()) {
            for (String paragraph : PARAGRAPH_BREAK.split(page.text())) {
                String trimmed = paragraph.strip();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (isHeading(trimmed)) {
                    currentSection = trimmed;
                }

                for (String segment : splitIfOversized(trimmed, config.getTargetCharacters())) {
                    if (bufferPage == null) {
                        bufferPage = page.pageNumber();
                        bufferSection = currentSection;
                    }
                    if (buffer.length() + segment.length() + 2 > config.getTargetCharacters()
                            && buffer.length() >= config.getMinCharacters()) {
                        chunks.add(toChunk(chunks.size(), buffer.toString(), bufferPage, bufferSection));
                        String overlap = tailOverlap(buffer.toString(), config.getOverlapCharacters());
                        buffer.setLength(0);
                        buffer.append(overlap);
                        bufferPage = page.pageNumber();
                        bufferSection = currentSection;
                    }
                    if (!buffer.isEmpty()) {
                        buffer.append("\n\n");
                    }
                    buffer.append(segment);
                }
            }
        }

        if (!buffer.isEmpty()) {
            String remainder = buffer.toString().strip();
            if (remainder.length() < config.getMinCharacters() && !chunks.isEmpty()) {
                TextChunk previous = chunks.remove(chunks.size() - 1);
                String merged = previous.content() + "\n\n" + remainder;
                chunks.add(toChunk(previous.index(), merged, previous.pageNumber(), previous.section()));
            } else if (!remainder.isEmpty()) {
                chunks.add(toChunk(chunks.size(), remainder, bufferPage, bufferSection));
            }
        }

        return chunks;
    }

    private List<String> splitIfOversized(String paragraph, int target) {
        if (paragraph.length() <= target) {
            return List.of(paragraph);
        }
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String sentence : SENTENCE_BREAK.split(paragraph)) {
            if (current.length() + sentence.length() + 1 > target && !current.isEmpty()) {
                parts.add(current.toString().strip());
                current.setLength(0);
            }
            if (sentence.length() > target) {
                // A single sentence longer than the target (tables, minified content):
                // fall back to hard slicing so nothing is silently dropped.
                for (int start = 0; start < sentence.length(); start += target) {
                    parts.add(sentence.substring(start, Math.min(sentence.length(), start + target)).strip());
                }
                continue;
            }
            current.append(current.isEmpty() ? "" : " ").append(sentence);
        }
        if (!current.isEmpty()) {
            parts.add(current.toString().strip());
        }
        parts.removeIf(String::isEmpty);
        return parts;
    }

    /**
     * Takes the last {@code overlap} characters, rounded outwards to a word boundary so the
     * carried-over text starts on a whole word.
     */
    private String tailOverlap(String text, int overlap) {
        if (overlap <= 0 || text.length() <= overlap) {
            return "";
        }
        String tail = text.substring(text.length() - overlap);
        int firstSpace = tail.indexOf(' ');
        return firstSpace > 0 ? tail.substring(firstSpace + 1).strip() : tail.strip();
    }

    private boolean isHeading(String text) {
        if (text.length() > MAX_SECTION_LENGTH || text.contains("\n")) {
            return false;
        }
        boolean endsWithSentencePunctuation = text.endsWith(".") || text.endsWith(",") || text.endsWith(";");
        boolean looksNumbered = text.matches("^(\\d+(\\.\\d+)*|[IVXLC]+\\.|#{1,6})\\s+.*");
        boolean mostlyUpperCase = text.equals(text.toUpperCase()) && text.matches(".*[A-Z].*");
        return !endsWithSentencePunctuation && (looksNumbered || mostlyUpperCase || text.split("\\s+").length <= 8);
    }

    private TextChunk toChunk(int index, String content, Integer page, String section) {
        String normalized = content.strip();
        return new TextChunk(index, normalized, page, truncateSection(section),
                normalized.length(), estimateTokens(normalized));
    }

    private String truncateSection(String section) {
        if (section == null) {
            return null;
        }
        return section.length() <= 255 ? section : section.substring(0, 252) + "...";
    }

    /**
     * Rough token estimate (~4 characters per token for English prose). Used for capacity
     * planning and context-window budgeting, not for billing.
     */
    public static int estimateTokens(String text) {
        return Math.max(1, text.length() / 4);
    }
}
