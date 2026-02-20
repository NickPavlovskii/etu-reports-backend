package com.etu.reports.api;

import com.etu.reports.dto.ArchiveReportDto;
import com.etu.reports.repository.StudentReportRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Archive", description = "Архив учебных работ")
public class ArchiveController {

    private final StudentReportRepository reportsRepo;

    public ArchiveController(StudentReportRepository reportsRepo) {
        this.reportsRepo = reportsRepo;
    }

    /**
     * Преподаватель (position = null/преподаватель): только свои отчёты
     * Возвращаем ТОЛЬКО ПОСЛЕДНИЕ ВЕРСИИ.
     */
    @GetMapping("/teachers/{lastName}/archive/reports")
    public List<ArchiveReportDto> teacherArchive(@PathVariable String lastName) {
        return reportsRepo.findArchiveLatestByTeacher(lastName);
    }

    /**
     * Админ/завед/зам: все отчёты кафедры (и можно фильтровать по преподавателю на бэке).
     * Возвращаем ТОЛЬКО ПОСЛЕДНИЕ ВЕРСИИ.
     *
     * teacherLastName — необязательный фильтр.
     */
    @GetMapping("/archive/reports")
    public List<ArchiveReportDto> archiveAll(
            @RequestParam(value = "teacherLastName", required = false) String teacherLastName
    ) {
        return reportsRepo.findArchiveLatestAll(teacherLastName);
    }
}
