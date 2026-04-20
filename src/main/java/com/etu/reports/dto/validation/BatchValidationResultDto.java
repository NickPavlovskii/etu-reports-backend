package com.etu.reports.dto.validation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

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

    public BatchValidationResultDto() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getAcademicYear() {
        return academicYear;
    }

    public void setAcademicYear(String academicYear) {
        this.academicYear = academicYear;
    }

    public String getWorkType() {
        return workType;
    }

    public void setWorkType(String workType) {
        this.workType = workType;
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

    public Integer getTemplateId() {
        return templateId;
    }

    public void setTemplateId(Integer templateId) {
        this.templateId = templateId;
    }

    public Boolean getAnnotate() {
        return annotate;
    }

    public void setAnnotate(Boolean annotate) {
        this.annotate = annotate;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public List<Item> getResults() {
        return results;
    }

    public void setResults(List<Item> results) {
        this.results = results;
    }

    public static final class Builder {
        private String academicYear;
        private String workType;
        private String controlType;
        private String topic;
        private Integer templateId;
        private Boolean annotate;
        private String error;
        private List<Item> results;

        public Builder academicYear(String academicYear) {
            this.academicYear = academicYear;
            return this;
        }

        public Builder workType(String workType) {
            this.workType = workType;
            return this;
        }

        public Builder controlType(String controlType) {
            this.controlType = controlType;
            return this;
        }

        public Builder topic(String topic) {
            this.topic = topic;
            return this;
        }

        public Builder templateId(Integer templateId) {
            this.templateId = templateId;
            return this;
        }

        public Builder annotate(Boolean annotate) {
            this.annotate = annotate;
            return this;
        }

        public Builder error(String error) {
            this.error = error;
            return this;
        }

        public Builder results(List<Item> results) {
            this.results = results;
            return this;
        }

        public BatchValidationResultDto build() {
            BatchValidationResultDto o = new BatchValidationResultDto();
            o.setAcademicYear(academicYear);
            o.setWorkType(workType);
            o.setControlType(controlType);
            o.setTopic(topic);
            o.setTemplateId(templateId);
            o.setAnnotate(annotate);
            o.setError(error);
            o.setResults(results);
            return o;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Item {
        @JsonProperty("filename")
        private String filename;

        @JsonProperty("result")
        private ValidationResultDto result;

        public Item() {
        }

        public Item(String filename, ValidationResultDto result) {
            this.filename = filename;
            this.result = result;
        }

        public static ItemBuilder builder() {
            return new ItemBuilder();
        }

        public String getFilename() {
            return filename;
        }

        public void setFilename(String filename) {
            this.filename = filename;
        }

        public ValidationResultDto getResult() {
            return result;
        }

        public void setResult(ValidationResultDto result) {
            this.result = result;
        }

        public static final class ItemBuilder {
            private String filename;
            private ValidationResultDto result;

            public ItemBuilder filename(String filename) {
                this.filename = filename;
                return this;
            }

            public ItemBuilder result(ValidationResultDto result) {
                this.result = result;
                return this;
            }

            public Item build() {
                return new Item(filename, result);
            }
        }
    }
}
