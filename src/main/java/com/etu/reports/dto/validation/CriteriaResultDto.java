package com.etu.reports.dto.validation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Результат проверки одного критерия.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CriteriaResultDto {

    @JsonProperty("code")
    private String code;

    @JsonProperty("title")
    private String title;

    @JsonProperty("passed")
    private boolean passed;

    @JsonProperty("message")
    private String message;

    @JsonProperty("expected")
    private String expected;

    @JsonProperty("actual")
    private String actual;

    @JsonProperty("level")
    private String level;

    public CriteriaResultDto() {
    }

    public CriteriaResultDto(String code, String title, boolean passed, String message, String expected, String actual, String level) {
        this.code = code;
        this.title = title;
        this.passed = passed;
        this.message = message;
        this.expected = expected;
        this.actual = actual;
        this.level = level;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public boolean isPassed() {
        return passed;
    }

    public void setPassed(boolean passed) {
        this.passed = passed;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getExpected() {
        return expected;
    }

    public void setExpected(String expected) {
        this.expected = expected;
    }

    public String getActual() {
        return actual;
    }

    public void setActual(String actual) {
        this.actual = actual;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public static final class Builder {
        private String code;
        private String title;
        private boolean passed;
        private String message;
        private String expected;
        private String actual;
        private String level;

        public Builder code(String code) {
            this.code = code;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder passed(boolean passed) {
            this.passed = passed;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder expected(String expected) {
            this.expected = expected;
            return this;
        }

        public Builder actual(String actual) {
            this.actual = actual;
            return this;
        }

        public Builder level(String level) {
            this.level = level;
            return this;
        }

        public CriteriaResultDto build() {
            return new CriteriaResultDto(code, title, passed, message, expected, actual, level);
        }
    }
}
