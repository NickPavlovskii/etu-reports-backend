package com.etu.reports.dto.report;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MoodleLinkDto {

    @JsonProperty("planRowId")
    private int planRowId;

    @JsonProperty("groupName")
    private String groupName;

    @JsonProperty("controlType")
    private String controlType;

    @JsonProperty("topic")
    private String topic;

    @JsonProperty("academicYear")
    private String academicYear;

    @JsonProperty("moodleUrl")
    private String moodleUrl;

    @JsonProperty("updatedAt")
    private String updatedAt;

    @JsonProperty("updatedBy")
    private String updatedBy;

    public int getPlanRowId() {
        return planRowId;
    }

    public void setPlanRowId(int planRowId) {
        this.planRowId = planRowId;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getControlType() {
        return controlType;
    }

    public void setControlType(String controlType) {
        this.controlType = controlType;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getAcademicYear() {
        return academicYear;
    }

    public void setAcademicYear(String academicYear) {
        this.academicYear = academicYear;
    }

    public String getMoodleUrl() {
        return moodleUrl;
    }

    public void setMoodleUrl(String moodleUrl) {
        this.moodleUrl = moodleUrl;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
