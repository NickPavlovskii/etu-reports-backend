package com.etu.reports.dto.validation;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Бакет распределения соответствия (для donut) */
public class ComplianceBucketDto {
    private String bucket;
    private long count;

    public ComplianceBucketDto() {}

    public ComplianceBucketDto(String bucket, long count) {
        this.bucket = bucket;
        this.count = count;
    }

    @JsonProperty("bucket")
    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }

    @JsonProperty("count")
    public long getCount() { return count; }
    public void setCount(long count) { this.count = count; }
}
