package com.etu.reports.dto.analytics;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Строка таблицы «Дисциплины»: дисциплина, группы, студенты, ожидается, загружено (X/Y) */
public class DisciplineTableRowDto {
    private String disciplineName;
    private int groupsCount;
    private int studentsCount;
    private long expectedCount;
    private long uploadedCount;

    @JsonProperty("disciplineName")
    public String getDisciplineName() { return disciplineName; }
    public void setDisciplineName(String disciplineName) { this.disciplineName = disciplineName; }

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
}
