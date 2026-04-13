package com.etu.reports.service;

import com.etu.reports.dto.admin.AdminStatsDto;
import com.etu.reports.dto.admin.AuditLogEntryDto;
import com.etu.reports.repository.AdminStatsRepository;
import com.etu.reports.repository.AnalyticsRepository;
import com.etu.reports.repository.AuditLogRepository;
import com.etu.reports.repository.VerificationTemplateRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

/**
 * Администрирование: сводная статистика, справочники, журнал аудита и откаты.
 */
@Service
public class AdminService {

    private final AdminStatsRepository statsRepo;
    private final AnalyticsRepository analyticsRepo;
    private final AuditLogRepository auditLogRepo;
    private final StudentReportService reportService;
    private final VerificationTemplateRepository templateRepo;

    public AdminService(AdminStatsRepository statsRepo, AnalyticsRepository analyticsRepo,
                        AuditLogRepository auditLogRepo, StudentReportService reportService,
                        VerificationTemplateRepository templateRepo) {
        this.statsRepo = statsRepo;
        this.analyticsRepo = analyticsRepo;
        this.auditLogRepo = auditLogRepo;
        this.reportService = reportService;
        this.templateRepo = templateRepo;
    }

    public AdminStatsDto getStats(String academicYear) {
        AdminStatsDto dto = statsRepo.getStats(academicYear);
        return dto != null ? dto : AdminStatsDto.of(0, 0, 0, 0);
    }

    public List<String> getDisciplines() {
        return analyticsRepo.getDisciplines();
    }

    public List<AuditLogEntryDto> findAuditLog(LocalDate dateFrom, LocalDate dateTo, String action, String actor) {
        return auditLogRepo.find(dateFrom, dateTo, action, actor);
    }

    public void rollbackAuditEntry(long id, String actor) {
        AuditLogEntryDto entry = auditLogRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Запись журнала не найдена: id=" + id));

        String action = entry.getAction();
        String entityId = entry.getEntityId();
        String who = actor != null && !actor.isBlank() ? actor : "admin";

        if ("REPORT_UPLOADED".equals(action)) {
            if (entityId == null || entityId.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Нет id отчёта для отката");
            }
            int reportId;
            try {
                reportId = Integer.parseInt(entityId.trim());
            } catch (NumberFormatException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неверный id отчёта: " + entityId);
            }
            if (!reportService.deleteReport(reportId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Отчёт не найден или уже удалён: id=" + reportId);
            }
            auditLogRepo.markRolledBack(id, who);
            auditLogRepo.log("ACTION_ROLLED_BACK", "report", entityId, who,
                    "Отменено: Загрузка работы (report_id=" + entityId + ")");
            return;
        }

        if ("TEMPLATE_CREATED".equals(action)) {
            if (entityId == null || entityId.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Нет id шаблона для отката");
            }
            int templateId;
            try {
                templateId = Integer.parseInt(entityId.trim());
            } catch (NumberFormatException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неверный id шаблона: " + entityId);
            }
            if (!templateRepo.deleteById(templateId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Шаблон не найден или уже удалён: id=" + templateId);
            }
            auditLogRepo.markRolledBack(id, who);
            auditLogRepo.log("ACTION_ROLLED_BACK", "template", entityId, who,
                    "Отменено: Создание шаблона (template_id=" + entityId + ")");
            return;
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Откат для действия \"" + action + "\" не поддерживается. Доступен откат для REPORT_UPLOADED и TEMPLATE_CREATED.");
    }
}
