package com.etu.reports.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Строка админки: дисциплина из рабочей программы и преподаватель по плану / фактически.
 */
public class DisciplineTeacherAssignmentDto {

    @JsonProperty("planRowId")
    private int planRowId;

    @JsonProperty("disciplineName")
    private String disciplineName;

    @JsonProperty("course")
    private Integer course;

    @JsonProperty("semester")
    private Integer semester;

    @JsonProperty("planLastName")
    private String planLastName;

    @JsonProperty("planFirstName")
    private String planFirstName;

    @JsonProperty("planPatronymic")
    private String planPatronymic;

    @JsonProperty("actualLastName")
    private String actualLastName;

    @JsonProperty("actualFirstName")
    private String actualFirstName;

    @JsonProperty("actualPatronymic")
    private String actualPatronymic;

    @JsonProperty("hasOverride")
    private boolean hasOverride;

    public int getPlanRowId() {
        return planRowId;
    }

    public void setPlanRowId(int planRowId) {
        this.planRowId = planRowId;
    }

    public String getDisciplineName() {
        return disciplineName;
    }

    public void setDisciplineName(String disciplineName) {
        this.disciplineName = disciplineName;
    }

    public Integer getCourse() {
        return course;
    }

    public void setCourse(Integer course) {
        this.course = course;
    }

    public Integer getSemester() {
        return semester;
    }

    public void setSemester(Integer semester) {
        this.semester = semester;
    }

    public String getPlanLastName() {
        return planLastName;
    }

    public void setPlanLastName(String planLastName) {
        this.planLastName = planLastName;
    }

    public String getPlanFirstName() {
        return planFirstName;
    }

    public void setPlanFirstName(String planFirstName) {
        this.planFirstName = planFirstName;
    }

    public String getPlanPatronymic() {
        return planPatronymic;
    }

    public void setPlanPatronymic(String planPatronymic) {
        this.planPatronymic = planPatronymic;
    }

    public String getActualLastName() {
        return actualLastName;
    }

    public void setActualLastName(String actualLastName) {
        this.actualLastName = actualLastName;
    }

    public String getActualFirstName() {
        return actualFirstName;
    }

    public void setActualFirstName(String actualFirstName) {
        this.actualFirstName = actualFirstName;
    }

    public String getActualPatronymic() {
        return actualPatronymic;
    }

    public void setActualPatronymic(String actualPatronymic) {
        this.actualPatronymic = actualPatronymic;
    }

    public boolean isHasOverride() {
        return hasOverride;
    }

    public void setHasOverride(boolean hasOverride) {
        this.hasOverride = hasOverride;
    }
}
