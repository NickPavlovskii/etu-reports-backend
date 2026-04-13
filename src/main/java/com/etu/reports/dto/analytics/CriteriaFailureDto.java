package com.etu.reports.dto.analytics;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Критерий, который чаще всего failing */
public class CriteriaFailureDto {
    private String criterion;
    private long failedCount;
    private long totalCount;

    @JsonProperty("criterion")
    public String getCriterion() { return criterion; }
    public void setCriterion(String criterion) { this.criterion = criterion; }

    @JsonProperty("failedCount")
    public long getFailedCount() { return failedCount; }
    public void setFailedCount(long failedCount) { this.failedCount = failedCount; }

    @JsonProperty("totalCount")
    public long getTotalCount() { return totalCount; }
    public void setTotalCount(long totalCount) { this.totalCount = totalCount; }
}
