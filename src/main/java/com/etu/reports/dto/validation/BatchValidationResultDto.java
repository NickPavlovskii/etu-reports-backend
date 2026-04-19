package com.etu.reports.dto.validation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BatchValidationResultDto {

    @JsonProperty("academicYear")
    private String academicYear;

    @JsonProperty("workType")
    private String workType;

    @JsonProperty("controlType")
    private String controlType;

    @JsonProperty("topic")
    private String topic;

    @JsonProperty("templateId")
    private Integer templateId;

    @JsonProperty("annotate")
    private Boolean annotate;

    @JsonProperty("error")
    private String error;

    @JsonProperty("results")
    private List<Item> results;

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Item {
        @JsonProperty("filename")
        private String filename;

        @JsonProperty("result")
        private ValidationResultDto result;
    }
}
