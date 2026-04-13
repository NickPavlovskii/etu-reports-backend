package com.etu.reports.dto.analytics;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Ошибки по дисциплинам — дисциплина, категория, количество */
public class ErrorsByDisciplineDto {
    private String disciplineName;
    private String category;
    private long count;

    @JsonProperty("disciplineName")
    public String getDisciplineName() { return disciplineName; }
    public void setDisciplineName(String disciplineName) { this.disciplineName = disciplineName; }

    @JsonProperty("category")
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    @JsonProperty("count")
    public long getCount() { return count; }
    public void setCount(long count) { this.count = count; }
}
