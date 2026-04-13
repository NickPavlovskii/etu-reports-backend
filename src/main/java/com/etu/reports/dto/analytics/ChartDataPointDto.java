package com.etu.reports.dto.analytics;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Точка данных для графика (месяц + значения) */
public class ChartDataPointDto {
    private String month; // ISO date or month label
    private long received;
    private long accepted;
    private long totalUploaded;
    private long checked;

    @JsonProperty("month")
    public String getMonth() { return month; }
    public void setMonth(String month) { this.month = month; }

    @JsonProperty("received")
    public long getReceived() { return received; }
    public void setReceived(long received) { this.received = received; }

    @JsonProperty("accepted")
    public long getAccepted() { return accepted; }
    public void setAccepted(long accepted) { this.accepted = accepted; }

    @JsonProperty("totalUploaded")
    public long getTotalUploaded() { return totalUploaded; }
    public void setTotalUploaded(long totalUploaded) { this.totalUploaded = totalUploaded; }

    @JsonProperty("checked")
    public long getChecked() { return checked; }
    public void setChecked(long checked) { this.checked = checked; }
}
