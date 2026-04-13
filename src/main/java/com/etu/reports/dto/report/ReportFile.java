package com.etu.reports.dto.report;

public record ReportFile(
        String fileName,
        String contentType,
        byte[] bytes
) {}
