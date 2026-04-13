package com.etu.reports.dto.teacher;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Строка «Статистика по преподавателям»: ФИО, кол-во дисциплин, ожидается работ, загружено */
public class TeacherSummaryRowDto {
    private String teacherFio;
    private String teacherLastName;
    private int disciplinesCount;
    private long expectedCount;
    private long uploadedCount;

    @JsonProperty("teacherFio")
    public String getTeacherFio() { return teacherFio; }
    public void setTeacherFio(String teacherFio) { this.teacherFio = teacherFio; }

    @JsonProperty("teacherLastName")
    public String getTeacherLastName() { return teacherLastName; }
    public void setTeacherLastName(String teacherLastName) { this.teacherLastName = teacherLastName; }

    @JsonProperty("disciplinesCount")
    public int getDisciplinesCount() { return disciplinesCount; }
    public void setDisciplinesCount(int disciplinesCount) { this.disciplinesCount = disciplinesCount; }

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
