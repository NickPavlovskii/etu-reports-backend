package com.etu.reports.api;

import com.etu.reports.dto.report.ArchiveReportDto;
import com.etu.reports.service.ArchiveService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Archive", description = "Архив учебных работ")
public class ArchiveController {

    private final ArchiveService archiveService;

    public ArchiveController(ArchiveService archiveService) {
        this.archiveService = archiveService;
    }

    @GetMapping("/teachers/{lastName}/archive/reports")
    public List<ArchiveReportDto> teacherArchive(@PathVariable String lastName) {
        return archiveService.findArchiveByTeacher(lastName);
    }

    @GetMapping("/archive/reports")
    public List<ArchiveReportDto> archiveAll(
            @RequestParam(value = "teacherLastName", required = false) String teacherLastName
    ) {
        return archiveService.findArchiveAll(teacherLastName);
    }
}
