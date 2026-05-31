package com.etu.reports.api;

import com.etu.reports.dto.admin.DisciplineTeacherAssignmentDto;
import com.etu.reports.exception.GlobalExceptionHandler;
import com.etu.reports.repository.AuditLogRepository;
import com.etu.reports.repository.DisciplineTeacherOverrideRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DisciplineTeacherAdminController.class)
@Import(GlobalExceptionHandler.class)
class DisciplineTeacherAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DisciplineTeacherOverrideRepository overrideRepo;

    @MockBean
    private AuditLogRepository auditLogRepo;

    @Test
    void list_returnsAssignments() throws Exception {
        DisciplineTeacherAssignmentDto dto = assignment(100, "Алгоритмы", "Иванов", true);
        when(overrideRepo.findAllAssignments("алг")).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/admin/discipline-teacher-assignments").param("search", "алг"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].planRowId").value(100))
                .andExpect(jsonPath("$[0].disciplineName").value("Алгоритмы"))
                .andExpect(jsonPath("$[0].hasOverride").value(true));
    }

    @Test
    void get_existingRow() throws Exception {
        when(overrideRepo.findByPlanRowId(42)).thenReturn(Optional.of(assignment(42, "Системная инженерия", "Петров", false)));

        mockMvc.perform(get("/api/admin/discipline-teacher-assignments/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planRowId").value(42))
                .andExpect(jsonPath("$.actualLastName").value("Петров"));
    }

    @Test
    void get_missingRow_returns404() throws Exception {
        when(overrideRepo.findByPlanRowId(999)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/admin/discipline-teacher-assignments/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_setsTeacherAndLogs() throws Exception {
        DisciplineTeacherAssignmentDto after = assignment(10, "Программирование", "Сидоров", true);
        when(overrideRepo.planRowExistsInLoad(10)).thenReturn(true);
        when(overrideRepo.findByPlanRowId(10)).thenReturn(Optional.empty(), Optional.of(after));

        mockMvc.perform(put("/api/admin/discipline-teacher-assignments/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actualLastName":"Сидоров","actualFirstName":"С.","updatedBy":"Админ"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actualLastName").value("Сидоров"));

        verify(overrideRepo).upsert(eq(10), eq("Сидоров"), eq("С."), eq(null), eq("Админ"));
        verify(auditLogRepo).log(
                eq("DISCIPLINE_TEACHER_SET"),
                eq("discipline_plan_row"),
                eq("10"),
                eq("Админ"),
                contains("planRowId=10"));
    }

    @Test
    void update_missingActualLastName_returns400() throws Exception {
        mockMvc.perform(put("/api/admin/discipline-teacher-assignments/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Укажите actualLastName"));
    }

    @Test
    void update_unknownPlanRow_returns404() throws Exception {
        when(overrideRepo.planRowExistsInLoad(77)).thenReturn(false);

        mockMvc.perform(put("/api/admin/discipline-teacher-assignments/77")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actualLastName":"Иванов"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_removesOverride() throws Exception {
        DisciplineTeacherAssignmentDto before = assignment(5, "Базы данных", "Козлов", true);
        DisciplineTeacherAssignmentDto after = assignment(5, "Базы данных", "Планов", false);
        when(overrideRepo.findByPlanRowId(5)).thenReturn(Optional.of(before), Optional.of(after));
        when(overrideRepo.deleteByPlanRowId(5)).thenReturn(1);

        mockMvc.perform(delete("/api/admin/discipline-teacher-assignments/5").param("actor", "Админ"))
                .andExpect(status().isNoContent());

        verify(overrideRepo).deleteByPlanRowId(5);
        verify(auditLogRepo).log(
                eq("DISCIPLINE_TEACHER_RESET"),
                eq("discipline_plan_row"),
                eq("5"),
                eq("Админ"),
                contains("planRowId=5"));
    }

    @Test
    void delete_noOverride_returns404() throws Exception {
        when(overrideRepo.deleteByPlanRowId(8)).thenReturn(0);

        mockMvc.perform(delete("/api/admin/discipline-teacher-assignments/8"))
                .andExpect(status().isNotFound());
    }

    private static DisciplineTeacherAssignmentDto assignment(
            int planRowId, String discipline, String actualLastName, boolean hasOverride) {
        DisciplineTeacherAssignmentDto dto = new DisciplineTeacherAssignmentDto();
        dto.setPlanRowId(planRowId);
        dto.setDisciplineName(discipline);
        dto.setActualLastName(actualLastName);
        dto.setHasOverride(hasOverride);
        return dto;
    }
}
