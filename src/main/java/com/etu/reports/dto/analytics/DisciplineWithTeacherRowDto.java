package com.etu.reports.dto.analytics;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * Строка таблицы «Дисциплины по кафедре»: дисциплина, ФИО преподавателя, курс, семестр, уровень/форма образования,
 * часы, виды контроля, группы, студенты, ожидается, загружено (X/Y).
 */
public class DisciplineWithTeacherRowDto {
    private Integer planRowId;
    private String disciplineName;
    private String teacherFio;
    private String teacherLastName;
    private Integer course;
    private Integer semester;
    private String educationLevel;
    private String educationForm;
    private BigDecimal lectureHours;
    private BigDecimal practiceHours;
    private Boolean hasExam;
    private Boolean hasPass;
    private Boolean hasPassMark;
    private List<String> groups;
    private int groupsCount;
    private int studentsCount;
    private long expectedCount;
    private long uploadedCount;
    /** Сколько Moodle-ссылок назначено для данного planRowId. */
    private long moodleLinksCount;

    @JsonProperty("planRowId")
    public Integer getPlanRowId() { return planRowId; }
    public void setPlanRowId(Integer planRowId) { this.planRowId = planRowId; }

    @JsonProperty("disciplineName")
    public String getDisciplineName() { return disciplineName; }
    public void setDisciplineName(String disciplineName) { this.disciplineName = disciplineName; }

    @JsonProperty("teacherFio")
    public String getTeacherFio() { return teacherFio; }
    public void setTeacherFio(String teacherFio) { this.teacherFio = teacherFio; }

    @JsonProperty("teacherLastName")
    public String getTeacherLastName() { return teacherLastName; }
    public void setTeacherLastName(String teacherLastName) { this.teacherLastName = teacherLastName; }

    @JsonProperty("course")
    public Integer getCourse() { return course; }
    public void setCourse(Integer course) { this.course = course; }

    @JsonProperty("semester")
    public Integer getSemester() { return semester; }
    public void setSemester(Integer semester) { this.semester = semester; }

    @JsonProperty("educationLevel")
    public String getEducationLevel() { return educationLevel; }
    public void setEducationLevel(String educationLevel) { this.educationLevel = educationLevel; }

    @JsonProperty("educationForm")
    public String getEducationForm() { return educationForm; }
    public void setEducationForm(String educationForm) { this.educationForm = educationForm; }

    @JsonProperty("lectureHours")
    public BigDecimal getLectureHours() { return lectureHours; }
    public void setLectureHours(BigDecimal lectureHours) { this.lectureHours = lectureHours; }

    @JsonProperty("practiceHours")
    public BigDecimal getPracticeHours() { return practiceHours; }
    public void setPracticeHours(BigDecimal practiceHours) { this.practiceHours = practiceHours; }

    @JsonProperty("hasExam")
    public Boolean getHasExam() { return hasExam; }
    public void setHasExam(Boolean hasExam) { this.hasExam = hasExam; }

    @JsonProperty("hasPass")
    public Boolean getHasPass() { return hasPass; }
    public void setHasPass(Boolean hasPass) { this.hasPass = hasPass; }

    @JsonProperty("hasPassMark")
    public Boolean getHasPassMark() { return hasPassMark; }
    public void setHasPassMark(Boolean hasPassMark) { this.hasPassMark = hasPassMark; }

    @JsonProperty("groups")
    public List<String> getGroups() { return groups; }
    public void setGroups(List<String> groups) { this.groups = groups; }

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

    @JsonProperty("moodleLinksCount")
    public long getMoodleLinksCount() { return moodleLinksCount; }
    public void setMoodleLinksCount(long moodleLinksCount) { this.moodleLinksCount = moodleLinksCount; }
}
