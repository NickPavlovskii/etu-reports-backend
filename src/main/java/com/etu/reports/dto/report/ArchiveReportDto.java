package com.etu.reports.dto.report;

public record ArchiveReportDto(
        int id,
        int planRowId,
        int studentId,
        String studentName,
        String groupName,
        String disciplineName,
        String teacherLastName,
        String topic,
        String controlType,
        String workType,
        String workTitle,
        String uploadDate,
        int version,
        Integer check,
        String status,
        String uploadedBy,
        String fileName
) {}
