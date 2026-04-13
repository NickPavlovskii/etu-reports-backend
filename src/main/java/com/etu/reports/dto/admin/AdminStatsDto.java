package com.etu.reports.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Общая статистика для страницы администрирования.
 * Поддерживаются оба набора имён полей для совместимости с фронтом.
 */
public class AdminStatsDto {

    @JsonProperty("teachersCount")
    private Integer teachersCount;
    @JsonProperty("disciplinesCount")
    private Integer disciplinesCount;
    @JsonProperty("groupsCount")
    private Integer groupsCount;
    @JsonProperty("templatesCount")
    private Integer templatesCount;

    @JsonProperty("employees")
    private Integer employees;
    @JsonProperty("disciplines")
    private Integer disciplines;
    @JsonProperty("groups")
    private Integer groups;
    @JsonProperty("templates")
    private Integer templates;

    public static AdminStatsDto of(int teachers, int disciplines, int groups, int templates) {
        AdminStatsDto dto = new AdminStatsDto();
        dto.setTeachersCount(teachers);
        dto.setDisciplinesCount(disciplines);
        dto.setGroupsCount(groups);
        dto.setTemplatesCount(templates);
        dto.setEmployees(teachers);
        dto.setDisciplines(disciplines);
        dto.setGroups(groups);
        dto.setTemplates(templates);
        return dto;
    }

    public Integer getTeachersCount() { return teachersCount; }
    public void setTeachersCount(Integer teachersCount) { this.teachersCount = teachersCount; }
    public Integer getDisciplinesCount() { return disciplinesCount; }
    public void setDisciplinesCount(Integer disciplinesCount) { this.disciplinesCount = disciplinesCount; }
    public Integer getGroupsCount() { return groupsCount; }
    public void setGroupsCount(Integer groupsCount) { this.groupsCount = groupsCount; }
    public Integer getTemplatesCount() { return templatesCount; }
    public void setTemplatesCount(Integer templatesCount) { this.templatesCount = templatesCount; }
    public Integer getEmployees() { return employees; }
    public void setEmployees(Integer employees) { this.employees = employees; }
    public Integer getDisciplines() { return disciplines; }
    public void setDisciplines(Integer disciplines) { this.disciplines = disciplines; }
    public Integer getGroups() { return groups; }
    public void setGroups(Integer groups) { this.groups = groups; }
    public Integer getTemplates() { return templates; }
    public void setTemplates(Integer templates) { this.templates = templates; }
}
