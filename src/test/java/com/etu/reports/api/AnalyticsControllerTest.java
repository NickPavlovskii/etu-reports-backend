package com.etu.reports.api;

import com.etu.reports.dto.analytics.DepartmentAnalyticsDto;
import com.etu.reports.dto.analytics.DisciplineTableRowDto;
import com.etu.reports.dto.analytics.DisciplineWithTeacherRowDto;
import com.etu.reports.dto.analytics.SemesterStatsRowDto;
import com.etu.reports.dto.teacher.TeacherStatsDto;
import com.etu.reports.dto.teacher.TeacherSummaryRowDto;
import com.etu.reports.exception.GlobalExceptionHandler;
import com.etu.reports.service.AnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AnalyticsController.class)
@Import(GlobalExceptionHandler.class)
class AnalyticsControllerTest {

    private static final String YEAR = "2024-2025";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnalyticsService analyticsService;

    @Test
    void teacherStats_withAcademicYear() throws Exception {
        TeacherStatsDto dto = new TeacherStatsDto();
        dto.setExpectedCount(54);
        dto.setTotalWorks(20);
        when(analyticsService.getTeacherStats("Иванов", YEAR)).thenReturn(dto);

        mockMvc.perform(get("/api/teachers/Иванов/stats").param("academicYear", YEAR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expectedCount").value(54))
                .andExpect(jsonPath("$.totalWorks").value(20))
                .andExpect(jsonPath("$.uploadedCount").value(20));
    }

    @Test
    void teacherStats_withDateFrom_resolvesYear() throws Exception {
        TeacherStatsDto dto = new TeacherStatsDto();
        dto.setExpectedCount(10);
        dto.setTotalWorks(5);
        when(analyticsService.getTeacherStats(eq("Петров"), eq(YEAR))).thenReturn(dto);

        mockMvc.perform(get("/api/teachers/Петров/stats").param("dateFrom", "2024-09-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expectedCount").value(10));

        verify(analyticsService).getTeacherStats("Петров", YEAR);
    }

    @Test
    void teacherStats_withoutYearOrDate_returns400() throws Exception {
        mockMvc.perform(get("/api/teachers/Иванов/stats"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void teacherStats_invalidAcademicYear_returns400() throws Exception {
        mockMvc.perform(get("/api/teachers/Иванов/stats").param("academicYear", "2024"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void teacherDisciplinesTable_returnsRows() throws Exception {
        DisciplineTableRowDto row = new DisciplineTableRowDto();
        row.setDisciplineName("Системная инженерия");
        row.setGroupsCount(2);
        row.setStudentsCount(50);
        row.setExpectedCount(100);
        row.setUploadedCount(37);
        when(analyticsService.getTeacherDisciplineStats(YEAR, "Иванов")).thenReturn(List.of(row));

        mockMvc.perform(get("/api/teachers/Иванов/stats/disciplines-table").param("academicYear", YEAR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].disciplineName").value("Системная инженерия"))
                .andExpect(jsonPath("$[0].uploadedCount").value(37));
    }

    @Test
    void teacherBySemester_returnsRows() throws Exception {
        SemesterStatsRowDto row = new SemesterStatsRowDto();
        row.setCourse(1);
        row.setSemester(2);
        row.setExpectedCount(30);
        row.setUploadedCount(12);
        when(analyticsService.getTeacherSemesterStats(YEAR, "Иванов")).thenReturn(List.of(row));

        mockMvc.perform(get("/api/teachers/Иванов/stats/by-semester").param("academicYear", YEAR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].course").value(1))
                .andExpect(jsonPath("$[0].semester").value(2));
    }

    @Test
    void departmentAnalytics_returnsPayload() throws Exception {
        DepartmentAnalyticsDto dto = new DepartmentAnalyticsDto();
        dto.setTotalTeachers(12);
        dto.setExpectedCount(500);
        dto.setTotalWorks(200);
        when(analyticsService.getDepartmentAnalytics(YEAR)).thenReturn(dto);

        mockMvc.perform(get("/api/admin/analytics").param("academicYear", YEAR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTeachers").value(12))
                .andExpect(jsonPath("$.expectedCount").value(500))
                .andExpect(jsonPath("$.totalWorks").value(200));
    }

    @Test
    void teachersSummary_returnsRows() throws Exception {
        TeacherSummaryRowDto row = new TeacherSummaryRowDto();
        row.setTeacherLastName("Иванов");
        row.setTeacherFio("Иванов И.И.");
        row.setDisciplinesCount(3);
        row.setExpectedCount(40);
        row.setUploadedCount(15);
        when(analyticsService.getTeacherSummary(YEAR)).thenReturn(List.of(row));

        mockMvc.perform(get("/api/admin/analytics/teachers-summary").param("academicYear", YEAR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].teacherLastName").value("Иванов"))
                .andExpect(jsonPath("$[0].disciplinesCount").value(3));
    }

    @Test
    void departmentDisciplinesTable_returnsRows() throws Exception {
        DisciplineTableRowDto row = new DisciplineTableRowDto();
        row.setDisciplineName("Программирование");
        row.setUploadedCount(8);
        when(analyticsService.getDepartmentDisciplineStats(YEAR)).thenReturn(List.of(row));

        mockMvc.perform(get("/api/admin/analytics/disciplines-table").param("academicYear", YEAR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].disciplineName").value("Программирование"));
    }

    @Test
    void departmentBySemester_returnsRows() throws Exception {
        SemesterStatsRowDto row = new SemesterStatsRowDto();
        row.setCourse(2);
        row.setSemester(1);
        when(analyticsService.getDepartmentSemesterStats(YEAR)).thenReturn(List.of(row));

        mockMvc.perform(get("/api/admin/analytics/by-semester").param("academicYear", YEAR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].course").value(2));
    }

    @Test
    void departmentAnalytics_withoutYearOrDate_returns400() throws Exception {
        mockMvc.perform(get("/api/admin/analytics"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void departmentDisciplinesWithTeachers_returnsRows() throws Exception {
        DisciplineWithTeacherRowDto row = new DisciplineWithTeacherRowDto();
        row.setPlanRowId(100);
        row.setDisciplineName("Алгоритмы");
        row.setTeacherLastName("Сидоров");
        row.setUploadedCount(5);
        when(analyticsService.getDepartmentDisciplineStatsWithTeachers(YEAR)).thenReturn(List.of(row));

        mockMvc.perform(get("/api/admin/analytics/disciplines-with-teachers").param("academicYear", YEAR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].planRowId").value(100))
                .andExpect(jsonPath("$[0].teacherLastName").value("Сидоров"));
    }
}
