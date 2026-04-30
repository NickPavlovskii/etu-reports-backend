package com.etu.reports.domain;

import java.math.BigDecimal;

public class DisciplineCard {
    private Integer planRowId;
    private String disciplineName;
    private Integer course;
    private Integer semester;

    private int groupsCount;
    private BigDecimal lectureHours;
    private BigDecimal practiceHours;

    private boolean hasExam;
    private boolean hasPass;
    private boolean hasPassMark;

    private String educationForm;
    private String educationLevel;

    public Integer getPlanRowId() { return planRowId; }
    public void setPlanRowId(Integer planRowId) { this.planRowId = planRowId; }

    public String getDisciplineName() { return disciplineName; }
    public void setDisciplineName(String disciplineName) { this.disciplineName = disciplineName; }

    public Integer getCourse() { return course; }
    public void setCourse(Integer course) { this.course = course; }

    public Integer getSemester() { return semester; }
    public void setSemester(Integer semester) { this.semester = semester; }

    public int getGroupsCount() { return groupsCount; }
    public void setGroupsCount(int groupsCount) { this.groupsCount = groupsCount; }

    public BigDecimal getLectureHours() { return lectureHours; }
    public void setLectureHours(BigDecimal lectureHours) { this.lectureHours = lectureHours; }

    public BigDecimal getPracticeHours() { return practiceHours; }
    public void setPracticeHours(BigDecimal practiceHours) { this.practiceHours = practiceHours; }

    public boolean isHasExam() { return hasExam; }
    public void setHasExam(boolean hasExam) { this.hasExam = hasExam; }

    public boolean isHasPass() { return hasPass; }
    public void setHasPass(boolean hasPass) { this.hasPass = hasPass; }

    public boolean isHasPassMark() { return hasPassMark; }
    public void setHasPassMark(boolean hasPassMark) { this.hasPassMark = hasPassMark; }

    public String getEducationForm() { return educationForm; }
    public void setEducationForm(String educationForm) { this.educationForm = educationForm; }

    public String getEducationLevel() { return educationLevel; }
    public void setEducationLevel(String educationLevel) { this.educationLevel = educationLevel; }
}
