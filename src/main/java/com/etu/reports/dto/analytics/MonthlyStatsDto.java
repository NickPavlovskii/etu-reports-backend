package com.etu.reports.dto.analytics;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Статистика по месяцам (динамика загрузок) */
public class MonthlyStatsDto {
    private String month;
    private long total;
    private long withValidation;
    private long passed;
    private Integer avgPercent;

    @JsonProperty("month")
    public String getMonth() { return month; }
    public void setMonth(String month) { this.month = month; }

    @JsonProperty("total")
    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }

    @JsonProperty("withValidation")
    public long getWithValidation() { return withValidation; }
    public void setWithValidation(long withValidation) { this.withValidation = withValidation; }

    @JsonProperty("passed")
    public long getPassed() { return passed; }
    public void setPassed(long passed) { this.passed = passed; }

    @JsonProperty("avgPercent")
    public Integer getAvgPercent() { return avgPercent; }
    public void setAvgPercent(Integer avgPercent) { this.avgPercent = avgPercent; }
}
