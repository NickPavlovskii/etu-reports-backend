package com.etu.reports.api;

import com.etu.reports.dto.teacher.TeacherProfileDto;
import com.etu.reports.repository.AuditLogRepository;
import com.etu.reports.repository.TeacherProfileRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API для администрирования преподавателей: список, редактирование роли и email.
 */
@RestController
@RequestMapping("/api/admin/teachers")
@Tag(name = "Teachers Admin", description = "Управление преподавателями (роли, email)")
public class TeachersAdminController {

    private final TeacherProfileRepository teacherProfileRepo;
    private final AuditLogRepository auditLogRepo;

    public TeachersAdminController(TeacherProfileRepository teacherProfileRepo, AuditLogRepository auditLogRepo) {
        this.teacherProfileRepo = teacherProfileRepo;
        this.auditLogRepo = auditLogRepo;
    }

    @Operation(summary = "Список преподавателей", description = "Все преподаватели кафедры с ФИО, email, ролью, кол-вом дисциплин")
    @GetMapping
    public List<TeacherProfileDto> list(
            @Parameter(description = "Поиск по имени или email")
            @RequestParam(value = "search", required = false) String search
    ) {
        return teacherProfileRepo.findAll(search);
    }

    @Operation(summary = "Получить преподавателя по фамилии")
    @GetMapping("/{lastName}")
    public TeacherProfileDto get(@PathVariable String lastName) {
        return teacherProfileRepo.findByLastName(lastName)
                .orElseThrow(() -> new IllegalArgumentException("Преподаватель не найден: " + lastName));
    }

    @Operation(summary = "Обновить роль и email преподавателя")
    @PutMapping("/{lastName}")
    public TeacherProfileDto updateRole(
            @PathVariable String lastName,
            @RequestBody(required = false) UpdateTeacherRoleRequest request,
            @Parameter(description = "Кто изменил (фамилия администратора), для журнала")
            @RequestParam(value = "actor", required = false) String actor
    ) {
        // Поддержка одной роли (role) или списка (roles): если есть «Администратор» — сохраняем ADMIN, иначе TEACHER
        String role = resolveRole(request);
        String email = request != null ? request.getEmail() : null;
        try {
            teacherProfileRepo.upsertRoleAndEmail(lastName, email, role);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw new IllegalArgumentException("Не удалось обновить профиль: " + msg + ". Выполните миграцию docs/migration/V3__teacher_profiles.sql");
        }
        auditLogRepo.log("ROLE_CHANGED", "teacher", lastName, actor, "role: " + role);
        return teacherProfileRepo.findByLastName(lastName)
                .orElseGet(() -> {
                    TeacherProfileDto dto = new TeacherProfileDto();
                    dto.setLastName(lastName);
                    dto.setFio(lastName);
                    dto.setEmail(email);
                    dto.setRole(role);
                    return dto;
                });
    }

    /** Из запроса (role или roles) определяем одну сохраняемую роль: ADMIN если есть админ, иначе TEACHER. */
    private static String resolveRole(UpdateTeacherRoleRequest request) {
        if (request == null) return "TEACHER";
        if (request.getRoles() != null && !request.getRoles().isEmpty()) {
            boolean hasAdmin = request.getRoles().stream()
                    .filter(r -> r != null && !r.isBlank())
                    .anyMatch(r -> {
                        String u = r.trim().toUpperCase();
                        return "ADMIN".equals(u) || "ADMINISTRATOR".equals(u)
                                || r.trim().toLowerCase().contains("админ");
                    });
            return hasAdmin ? "ADMIN" : "TEACHER";
        }
        if (request.getRole() != null && !request.getRole().isBlank()) {
            String r = request.getRole().trim().toUpperCase();
            if ("ADMINISTRATOR".equals(r)) return "ADMIN";
            if ("ADMIN".equals(r)) return "ADMIN";
            return "TEACHER";
        }
        return "TEACHER";
    }

    public static class UpdateTeacherRoleRequest {
        private String email;
        private String role;
        /** Список ролей с фронта (Преподаватель + Администратор): если есть админ — сохраняем ADMIN. */
        private List<String> roles;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public List<String> getRoles() { return roles; }
        public void setRoles(List<String> roles) { this.roles = roles; }
    }
}
