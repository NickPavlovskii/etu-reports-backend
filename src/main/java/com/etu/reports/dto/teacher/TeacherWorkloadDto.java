package com.etu.reports.dto.teacher;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Нагрузка по преподавателю */
public class TeacherWorkloadDto {
    private String teacher;
    private long workload;

    @JsonProperty("teacher")
    public String getTeacher() { return teacher; }
    public void setTeacher(String teacher) { this.teacher = teacher; }

    @JsonProperty("workload")
    public long getWorkload() { return workload; }
    public void setWorkload(long workload) { this.workload = workload; }
}
