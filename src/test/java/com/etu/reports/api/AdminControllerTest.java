package com.etu.reports.api;

import com.etu.reports.dto.admin.AdminStatsDto;
import com.etu.reports.dto.admin.AuditLogEntryDto;
import com.etu.reports.exception.GlobalExceptionHandler;
import com.etu.reports.service.AdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminController.class)
@Import(GlobalExceptionHandler.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminService adminService;

    @Test
    void stats_returnsPayload() throws Exception {
        when(adminService.getStats(null)).thenReturn(AdminStatsDto.of(5, 12, 8, 3));

        mockMvc.perform(get("/api/admin/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teachersCount").value(5))
                .andExpect(jsonPath("$.disciplinesCount").value(12))
                .andExpect(jsonPath("$.groupsCount").value(8))
                .andExpect(jsonPath("$.templatesCount").value(3));
    }

    @Test
    void stats_withAcademicYear_passesThrough() throws Exception {
        when(adminService.getStats("2024-2025")).thenReturn(AdminStatsDto.of(1, 2, 3, 4));

        mockMvc.perform(get("/api/admin/stats").param("academicYear", "2024-2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employees").value(1));
    }

    @Test
    void disciplines_returnsList() throws Exception {
        when(adminService.getDisciplines()).thenReturn(List.of("Системная инженерия", "Алгоритмы"));

        mockMvc.perform(get("/api/admin/disciplines"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("Системная инженерия"))
                .andExpect(jsonPath("$[1]").value("Алгоритмы"));
    }

    @Test
    void auditLog_returnsEntries() throws Exception {
        AuditLogEntryDto entry = AuditLogEntryDto.builder()
                .id(1L)
                .createdAt(Instant.parse("2025-01-15T10:00:00Z"))
                .action("REPORT_UPLOADED")
                .entityType("report")
                .entityId("42")
                .actor("Иванов")
                .details("file: test.docx")
                .build();
        when(adminService.findAuditLog(
                eq(LocalDate.of(2025, 1, 1)),
                eq(LocalDate.of(2025, 1, 31)),
                eq("REPORT_UPLOADED"),
                eq("Ива")))
                .thenReturn(List.of(entry));

        mockMvc.perform(get("/api/admin/audit-log")
                        .param("dateFrom", "2025-01-01")
                        .param("dateTo", "2025-01-31")
                        .param("action", "REPORT_UPLOADED")
                        .param("actor", "Ива"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].action").value("REPORT_UPLOADED"))
                .andExpect(jsonPath("$[0].actor").value("Иванов"));
    }

    @Test
    void auditLog_withoutFilters() throws Exception {
        when(adminService.findAuditLog(isNull(), isNull(), isNull(), isNull())).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/audit-log"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void rollback_returnsNoContent() throws Exception {
        mockMvc.perform(post("/api/admin/audit-log/7/rollback").param("actor", "Админ"))
                .andExpect(status().isNoContent());

        verify(adminService).rollbackAuditEntry(7L, "Админ");
    }

    @Test
    void rollback_notFound_propagatesStatus() throws Exception {
        doThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Запись журнала не найдена: id=99"))
                .when(adminService).rollbackAuditEntry(99L, null);

        mockMvc.perform(post("/api/admin/audit-log/99/rollback"))
                .andExpect(status().isNotFound());
    }
}
