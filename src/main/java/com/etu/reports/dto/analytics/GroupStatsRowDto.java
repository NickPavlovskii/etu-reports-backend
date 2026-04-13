package com.etu.reports.dto.analytics;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Строка «Статистика по группам»: группа, дисциплина, студенты, загружено X/Y, % по шаблону */
public class GroupStatsRowDto {
    private String groupName;
    private String disciplineName;
    private int studentsCount;
    private long uploaded;
    private long expected;
    private Integer avgPercent;

    @JsonProperty("groupName")
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    @JsonProperty("disciplineName")
    public String getDisciplineName() { return disciplineName; }
    public void setDisciplineName(String disciplineName) { this.disciplineName = disciplineName; }

    @JsonProperty("studentsCount")
    public int getStudentsCount() { return studentsCount; }
    public void setStudentsCount(int studentsCount) { this.studentsCount = studentsCount; }

    @JsonProperty("uploaded")
    public long getUploaded() { return uploaded; }
    public void setUploaded(long uploaded) { this.uploaded = uploaded; }

    @JsonProperty("expected")
    public long getExpected() { return expected; }
    public void setExpected(long expected) { this.expected = expected; }

    @JsonProperty("avgPercent")
    public Integer getAvgPercent() { return avgPercent; }
    public void setAvgPercent(Integer avgPercent) { this.avgPercent = avgPercent; }
}
