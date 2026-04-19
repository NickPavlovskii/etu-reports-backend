package com.etu.reports.service.validation.model;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class DocumentContext {

    private final String text;
    private final List<Paragraph> paragraphs;
    private final int pageCount;
    private final Set<String> fonts;
    private final Set<Double> fontSizes;

    public DocumentContext(String text, List<Paragraph> paragraphs, int pageCount,
                           Set<String> fonts, Set<Double> fontSizes) {
        this.text = text == null ? "" : text;
        this.paragraphs = paragraphs == null ? List.of() : List.copyOf(paragraphs);
        this.pageCount = pageCount;
        this.fonts = fonts == null ? Set.of() : Collections.unmodifiableSet(fonts);
        this.fontSizes = fontSizes == null ? Set.of() : Collections.unmodifiableSet(fontSizes);
    }

    public String getText() {
        return text;
    }

    public List<Paragraph> getParagraphs() {
        return paragraphs;
    }

    public int getPageCount() {
        return pageCount;
    }

    public Set<String> getFonts() {
        return fonts;
    }

    public Set<Double> getFontSizes() {
        return fontSizes;
    }
}
