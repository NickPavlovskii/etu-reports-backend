package com.etu.reports.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Запись журнала событий: кто, когда, какое действие, над каким объектом.
 */
public class AuditLogEntryDto {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("createdAt")
    private Instant createdAt;

    @JsonProperty("action")
    private String action;

    @JsonProperty("entityType")
    private String entityType;

    @JsonProperty("entityId")
    private String entityId;

    @JsonProperty("actor")
    private String actor;

    @JsonProperty("details")
    private String details;

    @JsonProperty("rolledBackAt")
    private Instant rolledBackAt;

    @JsonProperty("rolledBackBy")
    private String rolledBackBy;

    public AuditLogEntryDto() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public Instant getRolledBackAt() {
        return rolledBackAt;
    }

    public void setRolledBackAt(Instant rolledBackAt) {
        this.rolledBackAt = rolledBackAt;
    }

    public String getRolledBackBy() {
        return rolledBackBy;
    }

    public void setRolledBackBy(String rolledBackBy) {
        this.rolledBackBy = rolledBackBy;
    }

    public static final class Builder {
        private Long id;
        private Instant createdAt;
        private String action;
        private String entityType;
        private String entityId;
        private String actor;
        private String details;
        private Instant rolledBackAt;
        private String rolledBackBy;

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder action(String action) {
            this.action = action;
            return this;
        }

        public Builder entityType(String entityType) {
            this.entityType = entityType;
            return this;
        }

        public Builder entityId(String entityId) {
            this.entityId = entityId;
            return this;
        }

        public Builder actor(String actor) {
            this.actor = actor;
            return this;
        }

        public Builder details(String details) {
            this.details = details;
            return this;
        }

        public Builder rolledBackAt(Instant rolledBackAt) {
            this.rolledBackAt = rolledBackAt;
            return this;
        }

        public Builder rolledBackBy(String rolledBackBy) {
            this.rolledBackBy = rolledBackBy;
            return this;
        }

        public AuditLogEntryDto build() {
            AuditLogEntryDto o = new AuditLogEntryDto();
            o.setId(id);
            o.setCreatedAt(createdAt);
            o.setAction(action);
            o.setEntityType(entityType);
            o.setEntityId(entityId);
            o.setActor(actor);
            o.setDetails(details);
            o.setRolledBackAt(rolledBackAt);
            o.setRolledBackBy(rolledBackBy);
            return o;
        }
    }
}
