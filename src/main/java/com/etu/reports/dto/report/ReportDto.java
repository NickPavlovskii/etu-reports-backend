package com.etu.reports.dto.report;

public record ReportDto(
        int id,
        int planRowId,
        int studentId,
        String groupName,
        String teacherLastName,
        String topic,
        String controlType,
        String workType,
        String workTitle,
        String academicYear,
        String uploadDate,
        int version,
        Integer check,
        String status,
        String uploadedBy,
        String fileName,
        String filePath
) {}
