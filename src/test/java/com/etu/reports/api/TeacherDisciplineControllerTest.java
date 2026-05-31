package com.etu.reports.api;

import com.etu.reports.domain.DisciplineCard;
import com.etu.reports.dto.report.ReportDto;
import com.etu.reports.exception.GlobalExceptionHandler;
import com.etu.reports.repository.ControlScheduleRepository;
import com.etu.reports.repository.DisciplineTeacherOverrideRepository;
import com.etu.reports.repository.MoodleLinkRepository;
import com.etu.reports.repository.TeachingCardRepository;
import com.etu.reports.service.StudentReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TeacherDisciplineController.class)
@Import(GlobalExceptionHandler.class)
class TeacherDisciplineControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TeachingCardRepository teachingCardRepository;

    @MockBean
    private StudentReportService reportService;

    @MockBean
    private ControlScheduleRepository controlScheduleRepository;

    @MockBean
    private MoodleLinkRepository moodleLinkRepository;

    @MockBean
    private DisciplineTeacherOverrideRepository disciplineTeacherOverrideRepository;

    @Test
    void discipline_invalidAcademicYear_returns400() throws Exception {
        mockMvc.perform(get("/api/teachers/Иванов/discipline/123")
                        .param("academicYear", "bad"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void discipline_valid_returnsCard() throws Exception {
        DisciplineCard card = new DisciplineCard();
        card.setPlanRowId(123);
        card.setDisciplineName("Программирование");
        card.setCourse(2);
        card.setSemester(1);
        when(teachingCardRepository.findDisciplineCardByPlanRowId(eq("Иванов"), eq(123), eq("2024-2025")))
                .thenReturn(card);

        mockMvc.perform(get("/api/teachers/Иванов/discipline/123")
                        .param("academicYear", "2024-2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planRowId").value(123))
                .andExpect(jsonPath("$.disciplineName").value("Программирование"));
    }

    @Test
    void reports_returnsList() throws Exception {
        ReportDto r = new ReportDto(
                1, 123, 55, "3711", "Иванов", "тема", "лаб", "отчёт", "работа",
                "2024-2025", "2025-01-01", 1, 80, "Загружен", "Иванов", "f.docx", "/path");
        when(reportService.findReportsByTeacherAndDiscipline("Иванов", 123)).thenReturn(List.of(r));

        mockMvc.perform(get("/api/teachers/Иванов/discipline/123/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].studentId").value(55))
                .andExpect(jsonPath("$[0].fileName").value("f.docx"));
    }
}
