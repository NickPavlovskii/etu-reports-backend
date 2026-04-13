package com.etu.reports.dto.analytics;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Агрегат по курсу/семестру: сколько работ ожидается и сколько загружено */
public class SemesterStatsRowDto {
    private Integer course;
    private Integer semester;
    private int groupsCount;
    private int studentsCount;
    private long expectedCount;
    private long uploadedCount;

    @JsonProperty("course")
    public Integer getCourse() { return course; }
    public void setCourse(Integer course) { this.course = course; }

    @JsonProperty("semester")
    public Integer getSemester() { return semester; }
    public void setSemester(Integer semester) { this.semester = semester; }

    @JsonProperty("groupsCount")
    public int getGroupsCount() { return groupsCount; }
    public void setGroupsCount(int groupsCount) { this.groupsCount = groupsCount; }

    @JsonProperty("studentsCount")
    public int getStudentsCount() { return studentsCount; }
    public void setStudentsCount(int studentsCount) { this.studentsCount = studentsCount; }

    @JsonProperty("expectedCount")
    public long getExpectedCount() { return expectedCount; }
    public void setExpectedCount(long expectedCount) { this.expectedCount = expectedCount; }

    @JsonProperty("uploadedCount")
    public long getUploadedCount() { return uploadedCount; }
    public void setUploadedCount(long uploadedCount) { this.uploadedCount = uploadedCount; }

    @JsonProperty("totalWorks")
    public long getTotalWorks() { return uploadedCount; }

    public void setTotalWorks(long totalWorks) { this.uploadedCount = totalWorks; }
}
