package com.etu.reports.dto.analytics;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Строка детализации по группам — по загруженным отчётам и проверке по шаблону */
public class GroupDetailRowDto {
    private String groupNorm;
    private String disciplineName;
    private String controlType;
    private long total;
    private long withValidation;
    private long passed;
    private long failed;
    private Integer avgPercent;

    @JsonProperty("groupNorm")
    public String getGroupNorm() { return groupNorm; }
    public void setGroupNorm(String groupNorm) { this.groupNorm = groupNorm; }

    @JsonProperty("disciplineName")
    public String getDisciplineName() { return disciplineName; }
    public void setDisciplineName(String disciplineName) { this.disciplineName = disciplineName; }

    @JsonProperty("controlType")
    public String getControlType() { return controlType; }
    public void setControlType(String controlType) { this.controlType = controlType; }

    @JsonProperty("total")
    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }

    @JsonProperty("withValidation")
    public long getWithValidation() { return withValidation; }
    public void setWithValidation(long withValidation) { this.withValidation = withValidation; }

    @JsonProperty("passed")
    public long getPassed() { return passed; }
    public void setPassed(long passed) { this.passed = passed; }

    @JsonProperty("failed")
    public long getFailed() { return failed; }
    public void setFailed(long failed) { this.failed = failed; }

    @JsonProperty("avgPercent")
    public Integer getAvgPercent() { return avgPercent; }
    public void setAvgPercent(Integer avgPercent) { this.avgPercent = avgPercent; }
}
