package com.etu.reports.dto.analytics;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Тип работы и количество */
public class WorkTypeCountDto {
    private String workType;
    private long count;

    @JsonProperty("workType")
    public String getWorkType() { return workType; }
    public void setWorkType(String workType) { this.workType = workType; }

    @JsonProperty("count")
    public long getCount() { return count; }
    public void setCount(long count) { this.count = count; }
}
