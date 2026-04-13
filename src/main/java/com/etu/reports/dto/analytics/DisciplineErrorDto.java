package com.etu.reports.dto.analytics;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Ошибки по дисциплине и категории (для heatmap) */
public class DisciplineErrorDto {
    private String disciplineName;
    private String category;
    private long count;

    public DisciplineErrorDto() {}

    public DisciplineErrorDto(String disciplineName, String category, long count) {
        this.disciplineName = disciplineName;
        this.category = category;
        this.count = count;
    }

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
