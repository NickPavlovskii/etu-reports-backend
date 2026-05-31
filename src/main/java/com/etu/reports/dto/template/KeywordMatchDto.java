package com.etu.reports.dto.template;

import com.fasterxml.jackson.annotation.JsonProperty;

public class KeywordMatchDto {

    @JsonProperty("text")
    private String text;

    @JsonProperty("excerpt")
    private String excerpt;

    @JsonProperty("lineNumber")
    private int lineNumber;

    public KeywordMatchDto() {
    }

    public KeywordMatchDto(String text, String excerpt, int lineNumber) {
        this.text = text;
        this.excerpt = excerpt;
        this.lineNumber = lineNumber;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getExcerpt() {
        return excerpt;
    }

    public void setExcerpt(String excerpt) {
        this.excerpt = excerpt;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }
}
