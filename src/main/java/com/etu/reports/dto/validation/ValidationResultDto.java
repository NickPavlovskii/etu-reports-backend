package com.etu.reports.dto.validation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Результат проверки документа по шаблону.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ValidationResultDto {

    @JsonProperty("valid")
    private boolean valid;

    @JsonProperty("percent")
    private int percent;

    @JsonProperty("criteria")
    private List<CriteriaResultDto> criteria;

    @JsonProperty("errors")
    private List<CriteriaResultDto> errors;

    @JsonProperty("warnings")
    private List<CriteriaResultDto> warnings;

    @JsonProperty("errorMessage")
    private String errorMessage;

    @JsonProperty("annotatedFileBase64")
    private String annotatedFileBase64;

    @JsonProperty("annotatedFileName")
    private String annotatedFileName;

    public ValidationResultDto() {
    }

    public ValidationResultDto(
            boolean valid,
            int percent,
            List<CriteriaResultDto> criteria,
            List<CriteriaResultDto> errors,
            List<CriteriaResultDto> warnings,
            String errorMessage,
            String annotatedFileBase64,
            String annotatedFileName) {
        this.valid = valid;
        this.percent = percent;
        this.criteria = criteria;
        this.errors = errors;
        this.warnings = warnings;
        this.errorMessage = errorMessage;
        this.annotatedFileBase64 = annotatedFileBase64;
        this.annotatedFileName = annotatedFileName;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return builder()
                .valid(valid)
                .percent(percent)
                .criteria(criteria)
                .errors(errors)
                .warnings(warnings)
                .errorMessage(errorMessage)
                .annotatedFileBase64(annotatedFileBase64)
                .annotatedFileName(annotatedFileName);
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public int getPercent() {
        return percent;
    }

    public void setPercent(int percent) {
        this.percent = percent;
    }

    public List<CriteriaResultDto> getCriteria() {
        return criteria;
    }

    public void setCriteria(List<CriteriaResultDto> criteria) {
        this.criteria = criteria;
    }

    public List<CriteriaResultDto> getErrors() {
        return errors;
    }

    public void setErrors(List<CriteriaResultDto> errors) {
        this.errors = errors;
    }

    public List<CriteriaResultDto> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<CriteriaResultDto> warnings) {
        this.warnings = warnings;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getAnnotatedFileBase64() {
        return annotatedFileBase64;
    }

    public void setAnnotatedFileBase64(String annotatedFileBase64) {
        this.annotatedFileBase64 = annotatedFileBase64;
    }

    public String getAnnotatedFileName() {
        return annotatedFileName;
    }

    public void setAnnotatedFileName(String annotatedFileName) {
        this.annotatedFileName = annotatedFileName;
    }

    public static final class Builder {
        private boolean valid;
        private int percent;
        private List<CriteriaResultDto> criteria;
        private List<CriteriaResultDto> errors;
        private List<CriteriaResultDto> warnings;
        private String errorMessage;
        private String annotatedFileBase64;
        private String annotatedFileName;

        public Builder valid(boolean valid) {
            this.valid = valid;
            return this;
        }

        public Builder percent(int percent) {
            this.percent = percent;
            return this;
        }

        public Builder criteria(List<CriteriaResultDto> criteria) {
            this.criteria = criteria;
            return this;
        }

        public Builder errors(List<CriteriaResultDto> errors) {
            this.errors = errors;
            return this;
        }

        public Builder warnings(List<CriteriaResultDto> warnings) {
            this.warnings = warnings;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder annotatedFileBase64(String annotatedFileBase64) {
            this.annotatedFileBase64 = annotatedFileBase64;
            return this;
        }

        public Builder annotatedFileName(String annotatedFileName) {
            this.annotatedFileName = annotatedFileName;
            return this;
        }

        public ValidationResultDto build() {
            return new ValidationResultDto(
                    valid, percent, criteria, errors, warnings, errorMessage, annotatedFileBase64, annotatedFileName);
        }
    }
}
