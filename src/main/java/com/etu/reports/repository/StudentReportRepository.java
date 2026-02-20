package com.etu.reports.repository;

import com.etu.reports.config.AppProperties;
import com.etu.reports.dto.ArchiveReportDto;
import com.etu.reports.dto.ReportDto;
import com.etu.reports.dto.ReportFile;
import com.etu.reports.exception.ReportNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

@Repository
public class StudentReportRepository {

    private static final Logger log = LoggerFactory.getLogger(StudentReportRepository.class);

    private final JdbcTemplate jdbc;
    private final AppProperties appProperties;

    public StudentReportRepository(JdbcTemplate jdbc, AppProperties appProperties) {
        this.jdbc = jdbc;
        this.appProperties = appProperties;
    }

    /** Отчёты строго по дисциплине (plan_row_id) и преподавателю */
    public List<ReportDto> findReportsByTeacherAndDiscipline(String teacherLastName, int planRowId) {
        String sql = "SELECT id, plan_row_id, student_id, group_name, teacher_last_name, " +
                "topic, control_type, work_type, work_title, academic_year, " +
                "upload_date, version, check_percent, status, uploaded_by, file_name, file_path " +
                "FROM public.student_reports " +
                "WHERE plan_row_id = ? AND teacher_last_name ILIKE ? " +
                "ORDER BY upload_date DESC NULLS LAST, version DESC, id DESC";
        String pattern = "%" + (teacherLastName == null ? "" : teacherLastName.trim()) + "%";
        return jdbc.query(sql, REPORT_ROW_MAPPER, planRowId, pattern);
    }

    @Transactional
    public ReportDto saveReport(
            int planRowId,
            int studentId,
            String groupName,
            String teacherLastName,
            String topic,
            String controlType,
            String workType,
            String workTitle,
            String academicYear,
            boolean autoCheck,
            Integer check,
            String status,
            String uploadedBy,
            MultipartFile file
    ) {
        String safeGroup = groupName == null ? "" : groupName.trim();
        if (safeGroup.isEmpty() || "undefined".equalsIgnoreCase(safeGroup)) {
            throw new IllegalArgumentException("groupName is required");
        }

        String safeTopic = topic == null ? "" : topic.trim();
        String safeControlType = controlType == null ? "" : controlType.trim();
        if ("undefined".equalsIgnoreCase(safeControlType)) safeControlType = "";

        String safeWorkType = workType == null ? "" : workType.trim();
        String safeWorkTitle = workTitle == null ? "" : workTitle.trim();
        String safeAcademicYear = academicYear == null ? "" : academicYear.trim();
        String safeStatus = (status == null || status.isBlank()) ? "Загружен" : status.trim();
        String safeUploadedBy = uploadedBy == null ? "" : uploadedBy.trim();
        String safeTeacher = teacherLastName == null ? "" : teacherLastName.trim();

        // Версия: точное совпадение по teacher (без % для ILIKE)
        String nextVersionSql = "SELECT COALESCE(MAX(version), 0) + 1 AS v " +
                "FROM public.student_reports " +
                "WHERE plan_row_id = ? AND student_id = ? AND group_name = ? " +
                "  AND LOWER(TRIM(teacher_last_name)) = LOWER(TRIM(?)) " +
                "  AND COALESCE(topic,'') = COALESCE(NULLIF(?,''),'') " +
                "  AND COALESCE(control_type,'') = COALESCE(NULLIF(?,''),'') " +
                "  AND work_type = ? AND academic_year = ?";

        Integer version = jdbc.queryForObject(nextVersionSql, Integer.class,
                planRowId, studentId, safeGroup, safeTeacher,
                safeTopic, safeControlType, safeWorkType, safeAcademicYear);

        if (version == null) version = 1;

        Path storageDir = Paths.get(appProperties.getReportsStorageDir());
        try {
            Files.createDirectories(storageDir);
        } catch (Exception e) {
            throw new RuntimeException("Cannot create storage dir: " + storageDir, e);
        }

        String originalName = (file.getOriginalFilename() == null || file.getOriginalFilename().isBlank())
                ? "file" : file.getOriginalFilename();
        String safeOriginal = originalName.replaceAll("[\\\\/:*?\"<>|]", "_");
        String storedFileName = planRowId + "__" + safeGroup + "__" + studentId + "__"
                + LocalDate.now() + "__v" + version + "__" + safeOriginal;

        Path target = storageDir.resolve(storedFileName).normalize();
        try {
            Files.write(target, file.getBytes());
        } catch (Exception e) {
            throw new RuntimeException("Cannot write file: " + target, e);
        }

        log.info("Report saved: {}", target.toAbsolutePath());

        String insertSql = "INSERT INTO public.student_reports (" +
                "plan_row_id, student_id, group_name, teacher_last_name, " +
                "topic, control_type, work_type, work_title, academic_year, " +
                "upload_date, version, check_percent, status, uploaded_by, file_name, file_path" +
                ") VALUES (?, ?, ?, ?, NULLIF(?,''), NULLIF(?,''), ?, ?, ?, " +
                "CURRENT_DATE, ?, ?, ?, ?, ?, ?) " +
                "RETURNING id, plan_row_id, student_id, group_name, teacher_last_name, " +
                "topic, control_type, work_type, work_title, academic_year, upload_date, version, " +
                "check_percent, status, uploaded_by, file_name, file_path";

        return jdbc.queryForObject(insertSql, REPORT_ROW_MAPPER,
                planRowId, studentId, safeGroup, safeTeacher,
                safeTopic, safeControlType, safeWorkType, safeWorkTitle, safeAcademicYear,
                version, check, safeStatus, safeUploadedBy, originalName, target.toString());
    }

    public ReportFile getReportFile(int reportId) {
        String sql = "SELECT file_name, file_path FROM public.student_reports WHERE id = ?";
        List<ReportFile> list = jdbc.query(sql, (rs, rowNum) -> {
            String fileName = rs.getString("file_name");
            String filePath = rs.getString("file_path");
            if (filePath == null || filePath.isBlank()) {
                throw new ReportNotFoundException("Report " + reportId + " has no file path");
            }
            Path p = Paths.get(filePath);
            byte[] bytes;
            try {
                bytes = Files.readAllBytes(p);
            } catch (Exception e) {
                throw new ReportNotFoundException("Cannot read file for report " + reportId, e);
            }
            String ct = null;
            try {
                ct = Files.probeContentType(p);
            } catch (Exception ignored) {}
            if (ct == null) ct = "application/octet-stream";
            return new ReportFile(fileName, ct, bytes);
        }, reportId);

        if (list.isEmpty()) {
            throw new ReportNotFoundException("Report not found: " + reportId);
        }
        return list.get(0);
    }

    public List<ArchiveReportDto> findArchiveLatestByTeacher(String teacherLastName) {
        String sql = "WITH ranked AS (" +
                "  SELECT r.*, ROW_NUMBER() OVER (" +
                "    PARTITION BY r.plan_row_id, r.student_id, lower(btrim(r.group_name)), " +
                "      lower(btrim(r.teacher_last_name)), lower(COALESCE(btrim(r.topic),'')), " +
                "      lower(COALESCE(btrim(r.control_type),'')), COALESCE(btrim(r.academic_year),'') " +
                "    ORDER BY r.version DESC, r.upload_date DESC NULLS LAST, r.id DESC) AS rn " +
                "  FROM public.student_reports r WHERE r.teacher_last_name ILIKE ?" +
                ") SELECT l.id, l.plan_row_id, l.student_id, COALESCE(st.record_book,'') AS student_name, l.group_name, " +
                "  COALESCE(tl.discipline_name,'') AS discipline_name, l.teacher_last_name, " +
                "  l.topic, l.control_type, l.work_title, l.academic_year, l.upload_date, " +
                "  l.version, l.check_percent, l.status, l.uploaded_by, l.file_name " +
                "FROM ranked l " +
                "LEFT JOIN LATERAL (SELECT s.record_book FROM public.students s WHERE s.lk_id = l.student_id " +
                "  ORDER BY s.academic_year DESC NULLS LAST, s.record_book DESC NULLS LAST LIMIT 1) st ON TRUE " +
                "LEFT JOIN LATERAL (SELECT t.discipline_name FROM public.teaching_load_csv t " +
                "  WHERE t.plan_row_id = l.plan_row_id AND t.discipline_name IS NOT NULL AND t.discipline_name <> '' " +
                "  ORDER BY t.discipline_name LIMIT 1) tl ON TRUE " +
                "WHERE l.rn = 1 ORDER BY l.upload_date DESC NULLS LAST, l.id DESC";

        String ln = teacherLastName == null ? "" : teacherLastName.trim();
        return jdbc.query(sql, ARCHIVE_ROW_MAPPER, "%" + ln + "%");
    }

    public List<ArchiveReportDto> findArchiveLatestAll(String teacherLastNameFilter) {
        String sql = "WITH ranked AS (" +
                "  SELECT r.*, ROW_NUMBER() OVER (" +
                "    PARTITION BY r.plan_row_id, r.student_id, lower(btrim(r.group_name)), " +
                "      lower(btrim(r.teacher_last_name)), lower(COALESCE(btrim(r.control_type),'')), COALESCE(btrim(r.academic_year),'') " +
                "    ORDER BY r.version DESC, r.upload_date DESC NULLS LAST, r.id DESC) AS rn " +
                "  FROM public.student_reports r " +
                "  WHERE (? IS NULL OR ? = '' OR r.teacher_last_name ILIKE ?)" +
                ") SELECT l.id, l.plan_row_id, l.student_id, COALESCE(st.record_book,'') AS student_name, btrim(l.group_name) AS group_name, " +
                "  COALESCE(tl.discipline_name,'') AS discipline_name, l.teacher_last_name, l.topic, l.control_type, " +
                "  l.work_title, l.academic_year, l.upload_date, l.version, l.check_percent, l.status, l.uploaded_by, l.file_name " +
                "FROM ranked l " +
                "LEFT JOIN LATERAL (SELECT s.record_book FROM public.students s WHERE s.lk_id = l.student_id " +
                "  ORDER BY s.academic_year DESC NULLS LAST, s.record_book DESC NULLS LAST LIMIT 1) st ON TRUE " +
                "LEFT JOIN LATERAL (SELECT t.discipline_name FROM public.teaching_load_csv t " +
                "  WHERE t.plan_row_id = l.plan_row_id AND t.discipline_name IS NOT NULL AND t.discipline_name <> '' " +
                "  ORDER BY t.discipline_name LIMIT 1) tl ON TRUE " +
                "WHERE l.rn = 1 ORDER BY l.upload_date DESC NULLS LAST, l.id DESC";

        String filt = teacherLastNameFilter == null ? "" : teacherLastNameFilter.trim();
        return jdbc.query(sql, ARCHIVE_ROW_MAPPER, filt, filt, "%" + filt + "%");
    }

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
