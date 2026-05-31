package com.etu.reports.dto.template;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class KeywordSearchResultDto {

    @JsonProperty("filename")
    private String filename;

    @JsonProperty("query")
    private String query;

    @JsonProperty("matches")
    private List<KeywordMatchDto> matches;

    public KeywordSearchResultDto() {
    }

    public KeywordSearchResultDto(String filename, String query, List<KeywordMatchDto> matches) {
        this.filename = filename;
        this.query = query;
        this.matches = matches;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public List<KeywordMatchDto> getMatches() {
        return matches;
    }

    public void setMatches(List<KeywordMatchDto> matches) {
        this.matches = matches;
    }
}
