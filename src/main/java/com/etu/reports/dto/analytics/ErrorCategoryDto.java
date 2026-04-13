package com.etu.reports.dto.analytics;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Категория ошибок для bar chart */
public class ErrorCategoryDto {
    private String category;
    private long count;

    public ErrorCategoryDto() {}

    public ErrorCategoryDto(String category, long count) {
        this.category = category;
        this.count = count;
    }

    @JsonProperty("category")
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    @JsonProperty("count")
    public long getCount() { return count; }
    public void setCount(long count) { this.count = count; }
}
