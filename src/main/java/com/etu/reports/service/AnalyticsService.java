package com.etu.reports.service;

import com.etu.reports.dto.analytics.DepartmentAnalyticsDto;
import com.etu.reports.dto.analytics.DisciplineTableRowDto;
import com.etu.reports.dto.analytics.DisciplineWithTeacherRowDto;
import com.etu.reports.dto.analytics.SemesterStatsRowDto;
import com.etu.reports.dto.teacher.TeacherStatsDto;
import com.etu.reports.dto.teacher.TeacherSummaryRowDto;
import com.etu.reports.repository.AnalyticsRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Слой приложения для аналитики загрузок: делегирует {@link AnalyticsRepository}.
 */
@Service
public class AnalyticsService {

    private final AnalyticsRepository analyticsRepository;

    public AnalyticsService(AnalyticsRepository analyticsRepository) {
        this.analyticsRepository = analyticsRepository;
    }

    public TeacherStatsDto getTeacherStats(String teacherLastName, String academicYear) {
        return analyticsRepository.getTeacherStats(teacherLastName, academicYear);
    }

    public List<DisciplineTableRowDto> getTeacherDisciplineStats(String academicYear, String teacherLastName) {
        return analyticsRepository.getTeacherDisciplineStats(academicYear, teacherLastName);
    }

    public List<SemesterStatsRowDto> getTeacherSemesterStats(String academicYear, String teacherLastName) {
        return analyticsRepository.getTeacherSemesterStats(academicYear, teacherLastName);
    }

    public DepartmentAnalyticsDto getDepartmentAnalytics(String academicYear) {
        return analyticsRepository.getDepartmentAnalytics(academicYear);
    }

    public List<TeacherSummaryRowDto> getTeacherSummary(String academicYear) {
        return analyticsRepository.getTeacherSummary(academicYear);
    }

    public List<DisciplineTableRowDto> getDepartmentDisciplineStats(String academicYear) {
        return analyticsRepository.getDepartmentDisciplineStats(academicYear);
    }

    public List<SemesterStatsRowDto> getDepartmentSemesterStats(String academicYear) {
        return analyticsRepository.getDepartmentSemesterStats(academicYear);
    }

    public List<DisciplineWithTeacherRowDto> getDepartmentDisciplineStatsWithTeachers(String academicYear) {
        return analyticsRepository.getDepartmentDisciplineStatsWithTeachers(academicYear);
    }
}
