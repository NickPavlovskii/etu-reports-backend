package com.etu.reports.service;

import com.etu.reports.dto.report.ArchiveReportDto;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Архив учебных работ: делегирует {@link StudentReportService}.
 */
@Service
public class ArchiveService {

    private final StudentReportService studentReportService;

    public ArchiveService(StudentReportService studentReportService) {
        this.studentReportService = studentReportService;
    }

    public List<ArchiveReportDto> findArchiveByTeacher(String lastName) {
        return studentReportService.findArchiveByTeacher(lastName);
    }

    public List<ArchiveReportDto> findArchiveAll(String teacherLastNameFilter) {
        return studentReportService.findArchiveAll(teacherLastNameFilter);
    }
}
