package com.etu.reports.service.validation.model;

/**
 * Абзац документа в унифицированном виде (DOCX/PDF).
 */
public class Paragraph {

    private final String text;

    public Paragraph(String text) {
        this.text = text == null ? "" : text;
    }

    public String getText() {
        return text;
    }
}
