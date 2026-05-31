package com.etu.reports.repository;

import com.etu.reports.dto.admin.AuditLogEntryDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class AuditLogRepository {

    private static final Logger log = LoggerFactory.getLogger(AuditLogRepository.class);
    private final JdbcTemplate jdbc;

    private static final RowMapper<AuditLogEntryDto> MAPPER = (rs, i) -> {
        Timestamp ts = rs.getTimestamp("created_at");
        Timestamp rbAt = rs.getTimestamp("rolled_back_at");
        return AuditLogEntryDto.builder()
                .id(rs.getObject("id", Long.class))
                .createdAt(ts == null ? null : ts.toInstant())
                .action(rs.getString("action"))
                .entityType(rs.getString("entity_type"))
                .entityId(rs.getString("entity_id"))
                .actor(rs.getString("actor"))
                .details(rs.getString("details"))
                .rolledBackAt(rbAt == null ? null : rbAt.toInstant())
                .rolledBackBy(rs.getString("rolled_back_by"))
                .build();
    };

    public AuditLogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Записать событие в журнал. Таблица audit_log должна существовать (миграция V4).
     */
    public void log(String action, String entityType, String entityId, String actor, String details) {
        try {
            String sql = "INSERT INTO public.audit_log (action, entity_type, entity_id, actor, details) VALUES (?, ?, ?, ?, ?)";
            jdbc.update(sql, action, entityType, entityId, actor != null ? actor : "system", details);
        } catch (Exception e) {
            log.warn("Не удалось записать в журнал audit_log (выполните миграцию V4): action={}, error={}", action, e.getMessage());
        }
    }

    private static final String SELECT_COLS = "id, created_at, action, entity_type, entity_id, actor, details, rolled_back_at, rolled_back_by";
    private static final String SELECT_COLS_LEGACY = "id, created_at, action, entity_type, entity_id, actor, details";

    private static final RowMapper<AuditLogEntryDto> MAPPER_LEGACY = (rs, i) -> {
        Timestamp ts = rs.getTimestamp("created_at");
        return AuditLogEntryDto.builder()
                .id(rs.getObject("id", Long.class))
                .createdAt(ts == null ? null : ts.toInstant())
                .action(rs.getString("action"))
                .entityType(rs.getString("entity_type"))
                .entityId(rs.getString("entity_id"))
                .actor(rs.getString("actor"))
                .details(rs.getString("details"))
                .rolledBackAt(null)
                .rolledBackBy(null)
                .build();
    };

    /** Найти запись журнала по id */
    public Optional<AuditLogEntryDto> findById(long id) {
        try {
            String sql = "SELECT " + SELECT_COLS + " FROM public.audit_log WHERE id = ?";
            List<AuditLogEntryDto> list = jdbc.query(sql, MAPPER, id);
            return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
        } catch (Exception e) {
            try {
                String sqlLegacy = "SELECT " + SELECT_COLS_LEGACY + " FROM public.audit_log WHERE id = ?";
                List<AuditLogEntryDto> list = jdbc.query(sqlLegacy, MAPPER_LEGACY, id);
                return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
            } catch (Exception e2) {
                return Optional.empty();
            }
        }
    }

    private void ensureRollbackColumns() {
        try {
            jdbc.execute("ALTER TABLE public.audit_log ADD COLUMN IF NOT EXISTS rolled_back_at TIMESTAMPTZ");
            jdbc.execute("ALTER TABLE public.audit_log ADD COLUMN IF NOT EXISTS rolled_back_by VARCHAR(255)");
            log.info("Колонки rolled_back_at, rolled_back_by в audit_log созданы или уже существуют");
        } catch (Exception e) {
            log.warn("Не удалось добавить колонки отката в audit_log: {}", e.getMessage());
        }
    }


    public void markRolledBack(long auditLogId, String rolledBackBy) {
        String who = rolledBackBy != null ? rolledBackBy : "admin";
        try {
            jdbc.update("UPDATE public.audit_log SET rolled_back_at = CURRENT_TIMESTAMP, rolled_back_by = ? WHERE id = ?",
                    who, auditLogId);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("rolled_back") || msg.contains("column") || msg.contains("SQL grammar")) {
                ensureRollbackColumns();
                try {
                    jdbc.update("UPDATE public.audit_log SET rolled_back_at = CURRENT_TIMESTAMP, rolled_back_by = ? WHERE id = ?",
                            who, auditLogId);
                } catch (Exception e2) {
                    log.warn("Не удалось пометить запись как отменённую после добавления колонок: id={}, error={}", auditLogId, e2.getMessage());
                }
            } else {
                log.warn("Не удалось пометить запись журнала как отменённую: id={}, error={}", auditLogId, msg);
            }
        }
    }

    public List<AuditLogEntryDto> find(LocalDate dateFrom, LocalDate dateTo, String action, String actor) {
        List<Object> args = new ArrayList<>();
        if (dateFrom != null) {
            args.add(Timestamp.valueOf(dateFrom.atStartOfDay()));
        }
        if (dateTo != null) {
            args.add(Timestamp.valueOf(dateTo.plusDays(1).atStartOfDay()));
        }
        if (action != null && !action.isBlank()) {
            args.add(action.trim());
        }
        if (actor != null && !actor.isBlank()) {
            args.add("%" + actor.trim() + "%");
        }
        try {
            StringBuilder sql = new StringBuilder(
                    "SELECT " + SELECT_COLS + " FROM public.audit_log WHERE 1=1");
            if (dateFrom != null) sql.append(" AND created_at >= ?");
            if (dateTo != null) sql.append(" AND created_at < ?");
            if (action != null && !action.isBlank()) sql.append(" AND action = ?");
            if (actor != null && !actor.isBlank()) sql.append(" AND LOWER(TRIM(actor)) LIKE LOWER(?)");
            sql.append(" ORDER BY created_at DESC LIMIT 500");
            return jdbc.query(sql.toString(), MAPPER, args.toArray());
        } catch (Exception e) {
            try {
                StringBuilder sqlLegacy = new StringBuilder(
                        "SELECT " + SELECT_COLS_LEGACY + " FROM public.audit_log WHERE 1=1");
                if (dateFrom != null) sqlLegacy.append(" AND created_at >= ?");
                if (dateTo != null) sqlLegacy.append(" AND created_at < ?");
                if (action != null && !action.isBlank()) sqlLegacy.append(" AND action = ?");
                if (actor != null && !actor.isBlank()) sqlLegacy.append(" AND LOWER(TRIM(actor)) LIKE LOWER(?)");
                sqlLegacy.append(" ORDER BY created_at DESC LIMIT 500");
                return jdbc.query(sqlLegacy.toString(), MAPPER_LEGACY, args.toArray());
            } catch (Exception e2) {
                return List.of();
            }
        }
    }
}
