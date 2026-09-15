package com.enterpriseai.hub.document;

import com.enterpriseai.hub.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextChunkerTest {

    private TextChunker chunker;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.getChunking().setTargetCharacters(400);
        properties.getChunking().setOverlapCharacters(60);
        properties.getChunking().setMinCharacters(80);
        chunker = new TextChunker(properties);
    }

    @Test
    @DisplayName("short documents produce a single chunk")
    void singleChunkForShortText() {
        ExtractedText text = ExtractedText.of(List.of(
                new ExtractedPage(1, "Cheque book requests are handled by the branch within two working days.")));

        List<TextChunk> chunks = chunker.chunk(text);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).pageNumber()).isEqualTo(1);
        assertThat(chunks.get(0).content()).contains("two working days");
    }

    @Test
    @DisplayName("long documents are split and every chunk stays within the target size")
    void splitsLongText() {
        String paragraph = "The customer onboarding process requires identity verification. "
                + "Each branch must retain the signed mandate for seven years. ";
        ExtractedText text = ExtractedText.of(List.of(new ExtractedPage(1, paragraph.repeat(20))));

        List<TextChunk> chunks = chunker.chunk(text);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk ->
                assertThat(chunk.characterCount()).isLessThanOrEqualTo(700));
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.content()).isNotBlank());
    }

    @Test
    @DisplayName("chunk indexes are contiguous and start at zero")
    void indexesAreContiguous() {
        ExtractedText text = ExtractedText.of(List.of(
                new ExtractedPage(1, ("Payment limits apply per channel. ".repeat(40))),
                new ExtractedPage(2, ("Card replacement follows the same approval flow. ".repeat(40)))));

        List<TextChunk> chunks = chunker.chunk(text);

        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).index()).isEqualTo(i);
        }
    }

    @Test
    @DisplayName("page numbers are preserved so citations can point at a page")
    void tracksPageNumbers() {
        ExtractedText text = ExtractedText.of(List.of(
                new ExtractedPage(1, "Page one content about stop payments. ".repeat(20)),
                new ExtractedPage(7, "Page seven content about transaction limits. ".repeat(20))));

        List<TextChunk> chunks = chunker.chunk(text);

        assertThat(chunks).extracting(TextChunk::pageNumber).contains(1, 7);
    }

    @Test
    @DisplayName("consecutive chunks overlap so a statement on a boundary is never lost")
    void consecutiveChunksOverlap() {
        StringBuilder body = new StringBuilder();
        for (int i = 1; i <= 40; i++) {
            body.append("Control step ").append(i)
                    .append(" requires the branch officer to verify mandate reference ").append(i).append(". ");
        }
        ExtractedText text = ExtractedText.of(List.of(new ExtractedPage(1, body.toString())));

        List<TextChunk> chunks = chunker.chunk(text);

        assertThat(chunks).hasSizeGreaterThan(1);
        String secondChunkHead = chunks.get(1).content().substring(0, 30);
        assertThat(chunks.get(0).content())
                .as("the head of chunk 2 must also appear at the end of chunk 1")
                .contains(secondChunkHead);
    }

    @Test
    @DisplayName("a heading above a passage is recorded as its section")
    void capturesSectionHeadings() {
        ExtractedText text = ExtractedText.of(List.of(new ExtractedPage(1, """
                3. Cheque Stop Payment Procedure

                A stop payment instruction must be received in writing and acknowledged by the branch
                manager before the cheque is flagged in the core banking system. The customer is charged
                the standard fee published in the tariff schedule.
                """)));

        List<TextChunk> chunks = chunker.chunk(text);

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(0).section()).contains("Cheque Stop Payment Procedure");
    }

    @Test
    @DisplayName("empty input produces no chunks rather than an empty chunk")
    void emptyInputProducesNothing() {
        assertThat(chunker.chunk(ExtractedText.of(List.of()))).isEmpty();
    }
}
