package com.etu.reports.dto.template;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class VerificationTemplateDto {
    @JsonProperty("id")
    private Integer id;
    @JsonProperty("name")
    private String name;
    @JsonProperty("description")
    private String description;
    @JsonProperty("criteria")
    private TemplateCriteriaDto criteria;
    @JsonProperty("createdAt")
    private Instant createdAt;
    @JsonProperty("updatedAt")
    private Instant updatedAt;

    public VerificationTemplateDto() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public TemplateCriteriaDto getCriteria() {
        return criteria;
    }

    public void setCriteria(TemplateCriteriaDto criteria) {
        this.criteria = criteria;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public static final class Builder {
        private Integer id;
        private String name;
        private String description;
        private TemplateCriteriaDto criteria;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(Integer id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder criteria(TemplateCriteriaDto criteria) {
            this.criteria = criteria;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public VerificationTemplateDto build() {
            VerificationTemplateDto o = new VerificationTemplateDto();
            o.setId(id);
            o.setName(name);
            o.setDescription(description);
            o.setCriteria(criteria);
            o.setCreatedAt(createdAt);
            o.setUpdatedAt(updatedAt);
            return o;
        }
    }
}
