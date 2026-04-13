package com.etu.reports.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Запись журнала событий: кто, когда, какое действие, над каким объектом.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
}
