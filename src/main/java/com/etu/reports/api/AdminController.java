package com.etu.reports.api;


import com.etu.reports.dto.admin.AdminStatsDto;
import com.etu.reports.dto.admin.AuditLogEntryDto;
import com.etu.reports.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")

@Tag(name = "Admin", description = "Администрирование, статистика")

public class AdminController {
    private final AdminService adminService;
    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @Operation(
         summary = "Общая статистика",
         description = "Количество преподавателей кафедры ИС, дисциплин, групп, шаблонов. Группы можно фильтровать по учебному году."

    )

    @GetMapping("/admin/stats")

    public AdminStatsDto stats(
        @Parameter(description = "Учебный год (YYYY-YYYY). Необязателен; для групп без года — из нагрузки.")
        @RequestParam(value = "academicYear", required = false) String academicYear

    ) {
        return adminService.getStats(academicYear);
    }



    @Operation(summary = "Все дисциплины кафедры", description = "Список уникальных названий дисциплин кафедры (без привязки к преподавателю)")

    @GetMapping("/admin/disciplines")

    public List<String> disciplines() {
        return adminService.getDisciplines();
    }

    @Operation(summary = "Журнал событий", description = "Кто и когда загрузил отчёт, создал/изменил/удалил шаблон, сменил роль. Требует миграцию V4 (audit_log).")
    @GetMapping("/admin/audit-log")

    public List<AuditLogEntryDto> auditLog(
            @Parameter(description = "Начало периода (YYYY-MM-DD)")
            @RequestParam(value = "dateFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @Parameter(description = "Конец периода (YYYY-MM-DD)")
            @RequestParam(value = "dateTo", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @Parameter(description = "Тип действия: REPORT_UPLOADED, TEMPLATE_CREATED, TEMPLATE_UPDATED, TEMPLATE_DELETED, ROLE_CHANGED")
            @RequestParam(value = "action", required = false) String action,
            @Parameter(description = "Фамилия исполнителя (поиск по подстроке)")
            @RequestParam(value = "actor", required = false) String actor
    ) {
        return adminService.findAuditLog(dateFrom, dateTo, action, actor);
    }


    @Operation(summary = "Отменить действие", description = "Откат действия из журнала: загрузка работы — удаление отчёта; создание шаблона — удаление шаблона. В журнал пишется запись ACTION_ROLLED_BACK.")
    @PostMapping("/admin/audit-log/{id}/rollback")
    @ResponseStatus(HttpStatus.NO_CONTENT)

    public void rollback(
        @PathVariable long id,
        @Parameter(description = "Кто отменил (для записи в журнал)")
        @RequestParam(value = "actor", required = false) String actor

    ) {
        adminService.rollbackAuditEntry(id, actor);
    }
}
