package com.etu.reports.dto.analytics;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Динамика загрузок по месяцам: загружено vs прогнано шаблоном */
public class UploadDynamicsDto {
    private String month;
    private long uploaded;
    private long checked;

    public UploadDynamicsDto() {}

    public UploadDynamicsDto(String month, long uploaded, long checked) {
        this.month = month;
        this.uploaded = uploaded;
        this.checked = checked;
    }

    @JsonProperty("month")
    public String getMonth() { return month; }
    public void setMonth(String month) { this.month = month; }

    @JsonProperty("uploaded")
    public long getUploaded() { return uploaded; }
    public void setUploaded(long uploaded) { this.uploaded = uploaded; }

    @JsonProperty("checked")
    public long getChecked() { return checked; }
    public void setChecked(long checked) { this.checked = checked; }
}
