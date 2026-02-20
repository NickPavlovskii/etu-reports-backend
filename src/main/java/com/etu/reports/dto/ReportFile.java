package com.etu.reports.dto;

public record ReportFile(
        String fileName,
        String contentType,
        byte[] bytes
) {}
