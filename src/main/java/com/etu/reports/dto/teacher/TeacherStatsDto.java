package com.etu.reports.dto.teacher;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TeacherStatsDto {
    private long expectedCount;
    private long totalWorks;

    @JsonProperty("expectedCount")
    public long getExpectedCount() { return expectedCount; }
    public void setExpectedCount(long expectedCount) { this.expectedCount = expectedCount; }

    @JsonProperty("totalWorks")
    public long getTotalWorks() { return totalWorks; }
    public void setTotalWorks(long totalWorks) { this.totalWorks = totalWorks; }

    /** Дублирует totalWorks — фронт подставляет uploadedCount при отсутствии totalWorks */
    @JsonProperty("uploadedCount")
    public long getUploadedCount() { return totalWorks; }

    public void setUploadedCount(long uploadedCount) { this.totalWorks = uploadedCount; }
}
