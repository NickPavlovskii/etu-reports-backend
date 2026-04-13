package com.etu.reports.dto.analytics;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Проблемная группа: низкий % и/или много невалидных */
public class ProblematicGroupDto {
    private String groupName;
    private long reports;
    private Integer avgPercent;
    private long invalidCount;

    @JsonProperty("groupName")
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    @JsonProperty("reports")
    public long getReports() { return reports; }
    public void setReports(long reports) { this.reports = reports; }

    @JsonProperty("avgPercent")
    public Integer getAvgPercent() { return avgPercent; }
    public void setAvgPercent(Integer avgPercent) { this.avgPercent = avgPercent; }

    @JsonProperty("invalidCount")
    public long getInvalidCount() { return invalidCount; }
    public void setInvalidCount(long invalidCount) { this.invalidCount = invalidCount; }
}
