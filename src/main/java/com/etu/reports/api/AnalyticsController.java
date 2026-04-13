package com.etu.reports.api;

import com.etu.reports.dto.analytics.DepartmentAnalyticsDto;
import com.etu.reports.dto.analytics.DisciplineTableRowDto;
import com.etu.reports.dto.analytics.DisciplineWithTeacherRowDto;
import com.etu.reports.dto.analytics.SemesterStatsRowDto;
import com.etu.reports.dto.teacher.TeacherStatsDto;
import com.etu.reports.dto.teacher.TeacherSummaryRowDto;
import com.etu.reports.service.AnalyticsService;
import com.etu.reports.util.AcademicYearValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Аналитика загрузок: «сколько загружено из скольких ожидается» (X из Y).
 * Отчёты фильтруются по {@code student_reports.academic_year}.
 * <p>
 * Параметр {@code academicYear} (2024-2025) предпочтителен. Для совместимости со старым фронтом
 * можно передать только {@code dateFrom} — год будет выведен по правилу сентябрь–август.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Analytics", description = "Загрузки отчётов: ожидается / загружено")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /** Приоритет: явный academicYear; иначе вывод из dateFrom (как в старых запросах с диапазоном дат). */
    private static String resolveAcademicYear(String academicYear, LocalDate dateFrom) {
        if (academicYear != null && !academicYear.isBlank()) {
            return AcademicYearValidator.requireValid(academicYear);
        }
        if (dateFrom != null) {
            return AcademicYearValidator.academicYearContaining(dateFrom);
        }
        throw new IllegalArgumentException("Укажите academicYear (например 2024-2025) или dateFrom для вывода учебного года");
    }

    // --- Преподаватель (своя нагрузка) ---

    @Operation(summary = "Итого по преподавателю", description = "expectedCount — ожидается работ, totalWorks — загружено за учебный год")
    @GetMapping("/teachers/{lastName}/stats")
    public TeacherStatsDto teacherStats(
            @PathVariable String lastName,
            @Parameter(example = "2024-2025") @RequestParam(value = "academicYear", required = false) String academicYear,
            @Parameter(description = "Если нет academicYear — по этой дате выводится учебный год (совместимость)")
            @RequestParam(value = "dateFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom
    ) {
        return analyticsService.getTeacherStats(lastName, resolveAcademicYear(academicYear, dateFrom));
    }

    @Operation(summary = "По предметам (преподаватель)", description = "По каждой дисциплине: группы, студенты, expectedCount, uploadedCount")
    @GetMapping("/teachers/{lastName}/stats/disciplines-table")
    public List<DisciplineTableRowDto> teacherDisciplinesTable(
            @PathVariable String lastName,
            @RequestParam(value = "academicYear", required = false) String academicYear,
            @RequestParam(value = "dateFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom
    ) {
        return analyticsService.getTeacherDisciplineStats(resolveAcademicYear(academicYear, dateFrom), lastName);
    }

    @Operation(summary = "По курсу и семестру (преподаватель)", description = "Агрегат по паре course/semester: ожидается / загружено")
    @GetMapping("/teachers/{lastName}/stats/by-semester")
    public List<SemesterStatsRowDto> teacherBySemester(
            @PathVariable String lastName,
            @RequestParam(value = "academicYear", required = false) String academicYear,
            @RequestParam(value = "dateFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom
    ) {
        return analyticsService.getTeacherSemesterStats(resolveAcademicYear(academicYear, dateFrom), lastName);
    }

    // --- Кафедра (админ) ---

    @Operation(summary = "Итого по кафедре", description = "totalTeachers, expectedCount, totalWorks за учебный год")
    @GetMapping("/admin/analytics")
    public DepartmentAnalyticsDto departmentAnalytics(
            @RequestParam(value = "academicYear", required = false) String academicYear,
            @RequestParam(value = "dateFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom
    ) {
        return analyticsService.getDepartmentAnalytics(resolveAcademicYear(academicYear, dateFrom));
    }

    @Operation(summary = "По преподавателям", description = "По каждому: дисциплин, expectedCount, uploadedCount")
    @GetMapping("/admin/analytics/teachers-summary")
    public List<TeacherSummaryRowDto> teachersSummary(
            @RequestParam(value = "academicYear", required = false) String academicYear,
            @RequestParam(value = "dateFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom
    ) {
        return analyticsService.getTeacherSummary(resolveAcademicYear(academicYear, dateFrom));
    }

    @Operation(summary = "По предметам (кафедра)", description = "По названию дисциплины: группы, студенты, expectedCount, uploadedCount")
    @GetMapping("/admin/analytics/disciplines-table")
    public List<DisciplineTableRowDto> departmentDisciplinesTable(
            @RequestParam(value = "academicYear", required = false) String academicYear,
            @RequestParam(value = "dateFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom
    ) {
        return analyticsService.getDepartmentDisciplineStats(resolveAcademicYear(academicYear, dateFrom));
    }

    @Operation(summary = "По курсу и семестру (кафедра)", description = "Агрегат по паре course/semester")
    @GetMapping("/admin/analytics/by-semester")
    public List<SemesterStatsRowDto> departmentBySemester(
            @RequestParam(value = "academicYear", required = false) String academicYear,
            @RequestParam(value = "dateFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom
    ) {
        return analyticsService.getDepartmentSemesterStats(resolveAcademicYear(academicYear, dateFrom));
    }

    @Operation(summary = "По предметам и преподавателям (детально)", description = "Строка на пару дисциплина+преподаватель+course+semester: группы, ожидается, загружено")
    @GetMapping("/admin/analytics/disciplines-with-teachers")
    public List<DisciplineWithTeacherRowDto> departmentDisciplinesWithTeachers(
            @RequestParam(value = "academicYear", required = false) String academicYear,
            @RequestParam(value = "dateFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom
    ) {
        return analyticsService.getDepartmentDisciplineStatsWithTeachers(resolveAcademicYear(academicYear, dateFrom));
    }
}
