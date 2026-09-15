package com.enterpriseai.hub.document;

import java.util.List;

public record ExtractedText(List<ExtractedPage> pages, Integer pageCount, int characterCount) {

    public static ExtractedText of(List<ExtractedPage> pages) {
        int characters = pages.stream().mapToInt(page -> page.text().length()).sum();
        return new ExtractedText(pages, pages.size(), characters);
    }

    public boolean isEmpty() {
        return characterCount == 0;
    }
}
