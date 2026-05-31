package com.etu.reports.repository;

import com.etu.reports.dto.report.ArchiveReportDto;
import com.etu.reports.dto.report.ReportDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с таблицей {@code public.student_reports}.
 *
 * <p>Содержит <em>только</em> SQL-операции (чистый data-access слой).
 * Вся бизнес-логика (санитизация, версионирование, файловый I/O, аудит)
 * вынесена в {@link com.etu.reports.service.StudentReportService}.
 */
@Repository
public class StudentReportRepository {

    private final JdbcTemplate jdbc;

    public StudentReportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Чтение
    // ─────────────────────────────────────────────────────────────────────────

    /** Список отчётов строго по дисциплине и преподавателю. */
    public List<ReportDto> findByTeacherAndDiscipline(String teacherLastName, int planRowId) {
        String sql = """
                SELECT id, plan_row_id, student_id, group_name, teacher_last_name,
                       topic, control_type, work_type, work_title, academic_year,
                       upload_date, version, check_percent, status, uploaded_by,
                       file_name, file_path
                FROM public.student_reports
                WHERE plan_row_id = ?
                  AND LOWER(TRIM(teacher_last_name)) = LOWER(TRIM(?))
                ORDER BY upload_date DESC NULLS LAST, version DESC, id DESC
                """;
        return jdbc.query(sql, REPORT_ROW_MAPPER, planRowId, teacherLastName);
    }

    public int getNextVersion(int planRowId, int studentId, String groupName,
                              String teacherLastName, String topic, String controlType,
                              String workType, String academicYear) {
        String sql = """
                SELECT COALESCE(MAX(version), 0) + 1
                FROM public.student_reports
                WHERE plan_row_id = ?
                  AND student_id = ?
                  AND group_name = ?
                  AND LOWER(TRIM(teacher_last_name)) = LOWER(TRIM(?))
                  AND COALESCE(topic, '')        = COALESCE(NULLIF(?, ''), '')
                  AND COALESCE(control_type, '') = COALESCE(NULLIF(?, ''), '')
                  AND work_type    = ?
                  AND academic_year = ?
                """;
        Integer v = jdbc.queryForObject(sql, Integer.class,
                planRowId, studentId, groupName, teacherLastName,
                topic, controlType, workType, academicYear);
        return v == null ? 1 : v;
    }

    public Optional<String> findFilePathById(int reportId) {
        List<String> paths = jdbc.query(
                "SELECT file_path FROM public.student_reports WHERE id = ?",
                (rs, i) -> rs.getString("file_path"),
                reportId);
        return paths.isEmpty() ? Optional.empty() : Optional.ofNullable(paths.get(0));
    }

    public Optional<FileRef> findFileRefById(int reportId) {
        List<FileRef> list = jdbc.query(
                "SELECT file_name, file_path FROM public.student_reports WHERE id = ?",
                (rs, i) -> new FileRef(rs.getString("file_name"), rs.getString("file_path")),
                reportId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Запись
    // ─────────────────────────────────────────────────────────────────────────

    public ReportDto insert(int planRowId, int studentId, String groupName,
                            String teacherLastName, String topic, String controlType,
                            String workType, String workTitle, String academicYear,
                            int version, Integer checkPercent, String status,
                            String uploadedBy, String originalFileName, String storedFilePath) {
        String sql = """
                INSERT INTO public.student_reports (
                    plan_row_id, student_id, group_name, teacher_last_name,
                    topic, control_type, work_type, work_title, academic_year,
                    upload_date, version, check_percent, status, uploaded_by,
                    file_name, file_path
                ) VALUES (
                    ?, ?, ?, ?,
                    NULLIF(?, ''), NULLIF(?, ''), ?, ?, ?,
                    CURRENT_DATE, ?, ?, ?, ?, ?, ?
                )
                RETURNING id, plan_row_id, student_id, group_name, teacher_last_name,
                          topic, control_type, work_type, work_title, academic_year,
                          upload_date, version, check_percent, status, uploaded_by,
                          file_name, file_path
                """;
        return jdbc.queryForObject(sql, REPORT_ROW_MAPPER,
                planRowId, studentId, groupName, teacherLastName,
                topic, controlType, workType, workTitle, academicYear,
                version, checkPercent, status, uploadedBy,
                originalFileName, storedFilePath);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Удаление
    // ─────────────────────────────────────────────────────────────────────────

    public void deleteValidationResultsByReportId(int reportId) {
        jdbc.update("DELETE FROM public.validation_results WHERE report_id = ?", reportId);
    }

    /**
     * Удаляет запись отчёта.
     *
     * @return количество удалённых строк (0 или 1)
     */
    public int deleteById(int reportId) {
        return jdbc.update("DELETE FROM public.student_reports WHERE id = ?", reportId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Архив (последние версии)
    // ─────────────────────────────────────────────────────────────────────────

    public List<ArchiveReportDto> findArchiveLatestByTeacher(String teacherLastName) {
        String sql = """
                WITH ranked AS (
                    SELECT r.*,
                           ROW_NUMBER() OVER (
                               PARTITION BY r.plan_row_id, r.student_id,
                                            lower(btrim(r.group_name)),
                                            lower(btrim(r.teacher_last_name)),
                                            lower(COALESCE(btrim(r.topic), '')),
                                            lower(COALESCE(btrim(r.control_type), '')),
                                            COALESCE(btrim(r.academic_year), '')
                               ORDER BY r.version DESC, r.upload_date DESC NULLS LAST, r.id DESC
                           ) AS rn
                    FROM public.student_reports r
                    WHERE LOWER(TRIM(r.teacher_last_name)) = LOWER(TRIM(?))
                )
                SELECT l.id, l.plan_row_id, l.student_id,
                    COALESCE(NULLIF(TRIM(
                        COALESCE(sf.last_name, '') || ' ' ||
                        COALESCE(sf.first_name, '') || ' ' ||
                        COALESCE(sf.patronymic, '')), ''),
                        st.record_book, '') AS student_name,
                    l.group_name,
                    COALESCE(tl.discipline_name, '') AS discipline_name,
                    l.teacher_last_name, l.topic, l.control_type, l.work_title,
                    l.academic_year, l.upload_date, l.version, l.check_percent,
                    l.status, l.uploaded_by, l.file_name
                FROM ranked l
                LEFT JOIN LATERAL (
                    SELECT s.record_book FROM public.students s
                    WHERE s.lk_id = l.student_id
                    ORDER BY s.academic_year DESC NULLS LAST, s.record_book DESC NULLS LAST
                    LIMIT 1
                ) st ON TRUE
                LEFT JOIN LATERAL (
                    SELECT f.last_name, f.first_name, f.patronymic
                    FROM public.student_fio f
                    WHERE f.id = l.student_id
                    LIMIT 1
                ) sf ON TRUE
                LEFT JOIN LATERAL (
                    SELECT t.discipline_name FROM public.teaching_load_csv t
                    WHERE t.plan_row_id = l.plan_row_id
                      AND t.discipline_name IS NOT NULL AND t.discipline_name <> ''
                    ORDER BY t.discipline_name
                    LIMIT 1
                ) tl ON TRUE
                WHERE l.rn = 1
                ORDER BY l.upload_date DESC NULLS LAST, l.id DESC
                """;
        return jdbc.query(sql, ARCHIVE_ROW_MAPPER, teacherLastName);
    }

    public List<ArchiveReportDto> findArchiveLatestAll(String teacherLastNameFilter) {
        boolean hasFilter = teacherLastNameFilter != null && !teacherLastNameFilter.isBlank();

        // Условие подставляется форматированием: один параметр вместо трёх
        String teacherWhere = hasFilter
                ? "LOWER(TRIM(r.teacher_last_name)) = LOWER(TRIM(?))"
                : "TRUE";

        String sql = """
                WITH ranked AS (
                    SELECT r.*,
                           ROW_NUMBER() OVER (
                               PARTITION BY r.plan_row_id, r.student_id,
                                            lower(btrim(r.group_name)),
                                            lower(btrim(r.teacher_last_name)),
                                            lower(COALESCE(btrim(r.control_type), '')),
                                            COALESCE(btrim(r.academic_year), '')
                               ORDER BY r.version DESC, r.upload_date DESC NULLS LAST, r.id DESC
                           ) AS rn
                    FROM public.student_reports r
                    WHERE %s
                )
                SELECT l.id, l.plan_row_id, l.student_id,
                    COALESCE(NULLIF(TRIM(
                        COALESCE(sf.last_name, '') || ' ' ||
                        COALESCE(sf.first_name, '') || ' ' ||
                        COALESCE(sf.patronymic, '')), ''),
                        st.record_book, '') AS student_name,
                    btrim(l.group_name) AS group_name,
                    COALESCE(tl.discipline_name, '') AS discipline_name,
                    l.teacher_last_name, l.topic, l.control_type,
                    l.work_title, l.academic_year, l.upload_date, l.version,
                    l.check_percent, l.status, l.uploaded_by, l.file_name
                FROM ranked l
                LEFT JOIN LATERAL (
                    SELECT s.record_book FROM public.students s
                    WHERE s.lk_id = l.student_id
                    ORDER BY s.academic_year DESC NULLS LAST, s.record_book DESC NULLS LAST
                    LIMIT 1
                ) st ON TRUE
                LEFT JOIN LATERAL (
                    SELECT f.last_name, f.first_name, f.patronymic
                    FROM public.student_fio f
                    WHERE f.id = l.student_id
                    LIMIT 1
                ) sf ON TRUE
                LEFT JOIN LATERAL (
                    SELECT t.discipline_name FROM public.teaching_load_csv t
                    WHERE t.plan_row_id = l.plan_row_id
                      AND t.discipline_name IS NOT NULL AND t.discipline_name <> ''
                    ORDER BY t.discipline_name
                    LIMIT 1
                ) tl ON TRUE
                WHERE l.rn = 1
                ORDER BY l.upload_date DESC NULLS LAST, l.id DESC
                """.formatted(teacherWhere);

        return hasFilter
                ? jdbc.query(sql, ARCHIVE_ROW_MAPPER, teacherLastNameFilter.trim())
                : jdbc.query(sql, ARCHIVE_ROW_MAPPER);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Вспомогательный тип и маперы
    // ─────────────────────────────────────────────────────────────────────────

    public record FileRef(String fileName, String filePath) {}

    private static final RowMapper<ReportDto> REPORT_ROW_MAPPER = (rs, rowNum) -> {
        Date d = rs.getDate("upload_date");
        return new ReportDto(
                rs.getInt("id"),
                rs.getInt("plan_row_id"),
                rs.getInt("student_id"),
                rs.getString("group_name"),
                rs.getString("teacher_last_name"),
                rs.getString("topic"),
                rs.getString("control_type"),
                rs.getString("work_type"),
                rs.getString("work_title"),
                rs.getString("academic_year"),
                d == null ? null : d.toString(),
                rs.getInt("version"),
                (Integer) rs.getObject("check_percent"),
                rs.getString("status"),
                rs.getString("uploaded_by"),
                rs.getString("file_name"),
                rs.getString("file_path")
        );
    };

    private static final RowMapper<ArchiveReportDto> ARCHIVE_ROW_MAPPER = (rs, rowNum) -> {
        Date d = rs.getDate("upload_date");
        return new ArchiveReportDto(
                rs.getInt("id"),
                rs.getInt("plan_row_id"),
                rs.getInt("student_id"),
                rs.getString("student_name"),
                rs.getString("group_name"),
                rs.getString("discipline_name"),
                rs.getString("teacher_last_name"),
                rs.getString("topic"),
                rs.getString("control_type"),
                rs.getString("work_title"),
                rs.getString("academic_year"),
                d == null ? null : d.toString(),
                rs.getInt("version"),
                (Integer) rs.getObject("check_percent"),
                rs.getString("status"),
                rs.getString("uploaded_by"),
                rs.getString("file_name")
        );
    };
}
