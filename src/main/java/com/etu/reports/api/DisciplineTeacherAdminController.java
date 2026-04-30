package com.etu.reports.api;

import com.etu.reports.dto.admin.DisciplineTeacherAssignmentDto;
import com.etu.reports.dto.admin.UpdateDisciplineTeacherAssignmentRequest;
import com.etu.reports.repository.AuditLogRepository;
import com.etu.reports.repository.DisciplineTeacherOverrideRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Админка: преподаватель по рабочей программе (plan_row_id) и фактический ответственный.
 */
@RestController
@RequestMapping("/api/admin/discipline-teacher-assignments")
@Tag(name = "Admin discipline teachers", description = "Назначение фактического преподавателя по plan_row_id")
public class DisciplineTeacherAdminController {

    private final DisciplineTeacherOverrideRepository overrideRepo;
    private final AuditLogRepository auditLogRepo;

    public DisciplineTeacherAdminController(
            DisciplineTeacherOverrideRepository overrideRepo,
            AuditLogRepository auditLogRepo) {
        this.overrideRepo = overrideRepo;
        this.auditLogRepo = auditLogRepo;
    }

    @Operation(summary = "Список дисциплин и преподавателей (план + факт)",
            description = "План — из teaching_load_csv; факт — из discipline_teacher_override или совпадает с планом.")
    @GetMapping
    public List<DisciplineTeacherAssignmentDto> list(
            @Parameter(description = "Поиск по названию дисциплины или фамилии (план/факт)")
            @RequestParam(value = "search", required = false) String search
    ) {
        try {
            return overrideRepo.findAllAssignments(search);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, migrationHint(e), e);
        }
    }

    @Operation(summary = "Одна строка по plan_row_id")
    @GetMapping("/{planRowId}")
    public DisciplineTeacherAssignmentDto get(@PathVariable int planRowId) {
        try {
            return overrideRepo.findByPlanRowId(planRowId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Строка нагрузки не найдена для plan_row_id=" + planRowId));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, migrationHint(e), e);
        }
    }

    @Operation(summary = "Задать фактического преподавателя", description = "Фамилия обязательна; имя/отчество опционально.")
    @PutMapping("/{planRowId}")
    public DisciplineTeacherAssignmentDto update(
            @PathVariable int planRowId,
            @RequestBody(required = false) UpdateDisciplineTeacherAssignmentRequest body
    ) {
        if (body == null || body.getActualLastName() == null || body.getActualLastName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите actualLastName");
        }
        if (!overrideRepo.planRowExistsInLoad(planRowId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "plan_row_id не найден в teaching_load_csv для кафедры из app.department-filter: " + planRowId);
        }
        String ln = body.getActualLastName().trim();
        String fn = body.getActualFirstName() != null ? body.getActualFirstName().trim() : null;
        String pn = body.getActualPatronymic() != null ? body.getActualPatronymic().trim() : null;
        DisciplineTeacherAssignmentDto before = overrideRepo.findByPlanRowId(planRowId).orElse(null);
        try {
            overrideRepo.upsert(planRowId, ln, fn, pn, body.getUpdatedBy());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, migrationHint(e), e);
        }
        DisciplineTeacherAssignmentDto after = overrideRepo.findByPlanRowId(planRowId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось прочитать запись"));
        try {
            auditLogRepo.log("DISCIPLINE_TEACHER_SET", "discipline_plan_row", String.valueOf(planRowId),
                    body.getUpdatedBy(),
                    buildSetDetails(planRowId, before, after));
        } catch (Exception ignored) {
            // журнал опционален до миграции V4
        }
        return after;
    }

    @Operation(summary = "Сбросить фактического преподавателя", description = "Удаляет override; снова используется преподаватель из рабочей программы.")
    @DeleteMapping("/{planRowId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable int planRowId,
            @Parameter(description = "Кто сбросил (для журнала)")
            @RequestParam(value = "actor", required = false) String actor
    ) {
        DisciplineTeacherAssignmentDto before = overrideRepo.findByPlanRowId(planRowId).orElse(null);
        int n;
        try {
            n = overrideRepo.deleteByPlanRowId(planRowId);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, migrationHint(e), e);
        }
        if (n == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Переопределения для plan_row_id=" + planRowId + " нет");
        }
        DisciplineTeacherAssignmentDto after = overrideRepo.findByPlanRowId(planRowId).orElse(null);
        try {
            auditLogRepo.log("DISCIPLINE_TEACHER_RESET", "discipline_plan_row", String.valueOf(planRowId), actor,
                    buildResetDetails(planRowId, before, after));
        } catch (Exception ignored) { }
    }

    private static String buildSetDetails(int planRowId, DisciplineTeacherAssignmentDto before, DisciplineTeacherAssignmentDto after) {
        String discipline = after != null && after.getDisciplineName() != null ? after.getDisciplineName()
                : (before != null ? before.getDisciplineName() : null);
        String oldTeacher = before != null ? toFio(before.getActualLastName(), before.getActualFirstName(), before.getActualPatronymic()) : "не задан";
        String newTeacher = after != null ? toFio(after.getActualLastName(), after.getActualFirstName(), after.getActualPatronymic()) : "не задан";
        return "Дисциплина: " + safe(discipline)
                + " (planRowId=" + planRowId + "). "
                + "Фактический преподаватель изменён: \"" + oldTeacher + "\" -> \"" + newTeacher + "\".";
    }

    private static String buildResetDetails(int planRowId, DisciplineTeacherAssignmentDto before, DisciplineTeacherAssignmentDto after) {
        String discipline = before != null && before.getDisciplineName() != null ? before.getDisciplineName()
                : (after != null ? after.getDisciplineName() : null);
        String oldTeacher = before != null ? toFio(before.getActualLastName(), before.getActualFirstName(), before.getActualPatronymic()) : "не задан";
        String planTeacher = after != null
                ? toFio(after.getPlanLastName(), after.getPlanFirstName(), after.getPlanPatronymic())
                : "по рабочей программе";
        return "Дисциплина: " + safe(discipline)
                + " (planRowId=" + planRowId + "). "
                + "Переопределение снято: \"" + oldTeacher + "\" -> \"" + planTeacher + "\" (по рабочей программе).";
    }

    private static String toFio(String lastName, String firstName, String patronymic) {
        StringBuilder sb = new StringBuilder();
        if (lastName != null && !lastName.isBlank()) sb.append(lastName.trim());
        if (firstName != null && !firstName.isBlank()) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(firstName.trim());
        }
        if (patronymic != null && !patronymic.isBlank()) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(patronymic.trim());
        }
        return sb.isEmpty() ? "не указан" : sb.toString();
    }

    private static String safe(String s) {
        return (s == null || s.isBlank()) ? "без названия" : s.trim();
    }

    private static String migrationHint(Throwable e) {
        String base = "docs/migration/V5__discipline_teacher_override.sql: " + e.getMessage();
        String m = e.getMessage();
        if (m == null) {
            return base;
        }
        String lower = m.toLowerCase();
        if (m.contains("нет доступа к таблице")
                || lower.contains("permission denied")
                || lower.contains("insufficient privilege")) {
            return base + " — выдайте права роли приложения (DATABASE_USERNAME): "
                    + "GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.discipline_teacher_override TO <роль>; "
                    + "или заново выполните конец файла V5 (блок GRANT).";
        }
        return base;
    }
}
