package com.etu.reports.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UpdateDisciplineTeacherAssignmentRequest {

    @JsonProperty("actualLastName")
    private String actualLastName;

    @JsonProperty("actualFirstName")
    private String actualFirstName;

    @JsonProperty("actualPatronymic")
    private String actualPatronymic;

    @JsonProperty("updatedBy")
    private String updatedBy;

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

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
