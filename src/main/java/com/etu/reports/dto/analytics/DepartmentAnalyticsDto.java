package com.etu.reports.dto.analytics;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Статистика кафедры: сколько работ ожидается всего, сколько загружено */
public class DepartmentAnalyticsDto {
    private int totalTeachers;
    /** Ожидаемое количество работ по кафедре */
    private long expectedCount;
    /** Загружено отчётов по кафедре */
    private long totalWorks;

    @JsonProperty("totalTeachers")
    public int getTotalTeachers() { return totalTeachers; }
    public void setTotalTeachers(int totalTeachers) { this.totalTeachers = totalTeachers; }

    @JsonProperty("expectedCount")
    public long getExpectedCount() { return expectedCount; }
    public void setExpectedCount(long expectedCount) { this.expectedCount = expectedCount; }

    @JsonProperty("totalWorks")
    public long getTotalWorks() { return totalWorks; }
    public void setTotalWorks(long totalWorks) { this.totalWorks = totalWorks; }

    @JsonProperty("uploadedCount")
    public long getUploadedCount() { return totalWorks; }

    public void setUploadedCount(long uploadedCount) { this.totalWorks = uploadedCount; }
}
