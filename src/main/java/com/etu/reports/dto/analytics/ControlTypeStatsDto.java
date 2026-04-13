package com.etu.reports.dto.analytics;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Статистика по виду контроля */
public class ControlTypeStatsDto {
    private String controlType;
    private long total;
    private long withValidation;
    private long passed;
    private Integer avgPercent;

    @JsonProperty("controlType")
    public String getControlType() { return controlType; }
    public void setControlType(String controlType) { this.controlType = controlType; }

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
