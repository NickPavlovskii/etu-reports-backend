package com.etu.reports.service;

import com.etu.reports.dto.admin.AdminStatsDto;
import com.etu.reports.dto.admin.AuditLogEntryDto;
import com.etu.reports.repository.AdminStatsRepository;
import com.etu.reports.repository.AnalyticsRepository;
import com.etu.reports.repository.AuditLogRepository;
import com.etu.reports.repository.VerificationTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private AdminStatsRepository statsRepo;
    @Mock
    private AnalyticsRepository analyticsRepo;
    @Mock
    private AuditLogRepository auditLogRepo;
    @Mock
    private StudentReportService reportService;
    @Mock
    private VerificationTemplateRepository templateRepo;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(statsRepo, analyticsRepo, auditLogRepo, reportService, templateRepo);
    }

    @Test
    void getStats_nullFromRepo_returnsZeros() {
        when(statsRepo.getStats("2024-2025")).thenReturn(null);

        AdminStatsDto dto = adminService.getStats("2024-2025");

        assertThat(dto.getTeachersCount()).isZero();
        assertThat(dto.getDisciplinesCount()).isZero();
    }

    @Test
    void getDisciplines_delegatesToAnalytics() {
        when(analyticsRepo.getDisciplines()).thenReturn(List.of("Дисциплина A"));

        assertThat(adminService.getDisciplines()).containsExactly("Дисциплина A");
    }

    @Test
    void rollbackReportUploaded_deletesReportAndMarksAudit() {
        AuditLogEntryDto entry = AuditLogEntryDto.builder()
                .id(5L)
                .action("REPORT_UPLOADED")
                .entityId("101")
                .build();
        when(auditLogRepo.findById(5L)).thenReturn(Optional.of(entry));
        when(reportService.deleteReport(101)).thenReturn(true);

        adminService.rollbackAuditEntry(5L, "Админ");

        verify(reportService).deleteReport(101);
        verify(auditLogRepo).markRolledBack(5L, "Админ");
        verify(auditLogRepo).log(
                "ACTION_ROLLED_BACK", "report", "101", "Админ",
                "Отменено: Загрузка работы (report_id=101)");
    }

    @Test
    void rollbackTemplateCreated_deletesTemplate() {
        AuditLogEntryDto entry = AuditLogEntryDto.builder()
                .id(6L)
                .action("TEMPLATE_CREATED")
                .entityId("3")
                .build();
        when(auditLogRepo.findById(6L)).thenReturn(Optional.of(entry));
        when(templateRepo.deleteById(3)).thenReturn(true);

        adminService.rollbackAuditEntry(6L, "admin");

        verify(templateRepo).deleteById(3);
        verify(auditLogRepo).markRolledBack(6L, "admin");
    }

    @Test
    void rollback_unknownAction_throwsBadRequest() {
        AuditLogEntryDto entry = AuditLogEntryDto.builder()
                .id(1L)
                .action("ROLE_CHANGED")
                .entityId("Иванов")
                .build();
        when(auditLogRepo.findById(1L)).thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> adminService.rollbackAuditEntry(1L, "admin"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("не поддерживается");
    }

    @Test
    void rollback_missingEntry_throwsNotFound() {
        when(auditLogRepo.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.rollbackAuditEntry(404L, "admin"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("не найдена");
    }
}
