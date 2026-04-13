package com.etu.reports.repository;

import com.etu.reports.dto.analytics.*;
import com.etu.reports.dto.validation.ComplianceBucketDto;
import com.etu.reports.dto.teacher.TeacherStatsDto;
import com.etu.reports.dto.teacher.TeacherSummaryRowDto;
import com.etu.reports.dto.teacher.TeacherWorkloadDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@Repository
public class AnalyticsRepository {

    private static final String DEPT = "каф.ИС";

    private final JdbcTemplate jdbc;

    @Value("${app.department-filter:каф.ИС}")
    private String departmentFilter;

    public AnalyticsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private String dept() {
        return departmentFilter != null ? departmentFilter : DEPT;
    }

    /** Статистика преподавателя: ожидается работ, загружено (отчёты с academic_year = заданному году) */
    public TeacherStatsDto getTeacherStats(String teacherLastName, String academicYear) {
        String ln = teacherLastName == null ? "" : teacherLastName.trim();

        String sql = """
            WITH tl AS (
              SELECT DISTINCT t.plan_row_id, NULLIF(ltrim(btrim(t.group_name), '0'), '') AS group_norm
              FROM public.teaching_load_csv t
              WHERE LOWER(TRIM(t.last_name)) = LOWER(TRIM(?)) AND t.department_name ILIKE ?
            ),
            stud AS (
              SELECT NULLIF(ltrim(btrim(s.group_name), '0'), '') AS group_norm, COUNT(*)::int AS students_cnt
              FROM public.students s WHERE s.status IS DISTINCT FROM 'Отчислен' GROUP BY 1
            ),
            controls AS (
              SELECT plan_row_id, GREATEST(COALESCE(COUNT(DISTINCT control_text), 0), 1)::bigint AS controls_cnt
              FROM public.control_schedule GROUP BY plan_row_id
            ),
            expected_one AS (
              SELECT SUM(COALESCE(stud.students_cnt, 0) * COALESCE(c.controls_cnt, 1))::bigint AS expected_cnt
              FROM tl LEFT JOIN stud ON stud.group_norm = tl.group_norm
              LEFT JOIN controls c ON c.plan_row_id = tl.plan_row_id
            ),
            ranked AS (
              SELECT r.id, ROW_NUMBER() OVER (
                PARTITION BY r.plan_row_id, r.student_id, lower(btrim(r.group_name)),
                  lower(btrim(r.teacher_last_name)), lower(COALESCE(btrim(r.control_type),'')), COALESCE(btrim(r.academic_year),'')
                ORDER BY r.version DESC, r.id DESC
              ) AS rn
              FROM public.student_reports r
              WHERE LOWER(TRIM(r.teacher_last_name)) = LOWER(TRIM(?)) AND btrim(COALESCE(r.academic_year, '')) = ?
            )
            SELECT COALESCE((SELECT expected_cnt FROM expected_one), 0)::bigint AS expected_cnt,
              (SELECT COUNT(*) FROM ranked WHERE rn = 1)::bigint AS total_works
            """;
        try {
            return jdbc.queryForObject(sql, (rs, i) -> {
                TeacherStatsDto dto = new TeacherStatsDto();
                dto.setExpectedCount(rs.getLong("expected_cnt"));
                dto.setTotalWorks(rs.getLong("total_works"));
                return dto;
            }, ln, dept(), ln, academicYear);
        } catch (Exception e) {
            TeacherStatsDto dto = new TeacherStatsDto();
            dto.setExpectedCount(0);
            dto.setTotalWorks(0);
            return dto;
        }
    }

    /** Детализация по группам — группа, дисциплина, вид контроля, всего, с проверкой, прошли, ср. % */
    public List<GroupDetailRowDto> getTeacherGroupDetails(String teacherLastName, LocalDate dateFrom, LocalDate dateTo) {
        Date df = dateFrom != null ? Date.valueOf(dateFrom) : Date.valueOf("1900-01-01");
        Date dt = dateTo != null ? Date.valueOf(dateTo) : Date.valueOf("2100-12-31");
        String ln = teacherLastName == null ? "" : teacherLastName.trim();

        String sql = """
            WITH base AS (
              SELECT r.plan_row_id, r.group_name, r.teacher_last_name, r.control_type,
                COALESCE(tl.discipline_name, '') AS discipline_name, r.check_percent, vr.percent AS vr_percent, vr.valid,
                ROW_NUMBER() OVER (
                  PARTITION BY r.plan_row_id, r.student_id, lower(btrim(r.group_name)),
                    lower(btrim(r.teacher_last_name)), lower(COALESCE(btrim(r.control_type),'')),
                    COALESCE(btrim(r.academic_year),'')
                  ORDER BY r.version DESC, r.id DESC
                ) AS rn
              FROM public.student_reports r
              LEFT JOIN LATERAL (SELECT t.discipline_name FROM public.teaching_load_csv t WHERE t.plan_row_id = r.plan_row_id AND t.discipline_name IS NOT NULL LIMIT 1) tl ON TRUE
              LEFT JOIN LATERAL (SELECT percent, valid FROM public.validation_results vr2 WHERE vr2.report_id = r.id ORDER BY vr2.checked_at DESC LIMIT 1) vr ON TRUE
              WHERE LOWER(TRIM(r.teacher_last_name)) = LOWER(TRIM(?))
                AND r.upload_date >= ? AND r.upload_date <= ?
            )
            SELECT NULLIF(ltrim(btrim(group_name), '0'), '') AS group_norm, discipline_name,
              COALESCE(NULLIF(TRIM(control_type), ''), '—') AS control_type,
              COUNT(*)::bigint AS total,
              COUNT(*) FILTER (WHERE vr_percent IS NOT NULL OR valid IS NOT NULL)::bigint AS with_validation,
              COUNT(*) FILTER (WHERE valid = true)::bigint AS passed,
              COUNT(*) FILTER (WHERE valid = false)::bigint AS failed,
              ROUND(AVG(COALESCE(vr_percent, check_percent)) FILTER (WHERE COALESCE(vr_percent, check_percent) IS NOT NULL)::numeric, 0)::int AS avg_percent
            FROM base WHERE rn = 1
            GROUP BY group_name, discipline_name, control_type ORDER BY group_norm, discipline_name, control_type
            """;
        try {
            return jdbc.query(sql, (rs, i) -> {
                GroupDetailRowDto dto = new GroupDetailRowDto();
                dto.setGroupNorm(rs.getString("group_norm"));
                dto.setDisciplineName(rs.getString("discipline_name"));
                dto.setControlType(rs.getString("control_type"));
                dto.setTotal(rs.getLong("total"));
                dto.setWithValidation(rs.getLong("with_validation"));
                dto.setPassed(rs.getLong("passed"));
                dto.setFailed(rs.getLong("failed"));
                dto.setAvgPercent((Integer) rs.getObject("avg_percent"));
                return dto;
            }, ln, df, dt);
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Статистика кафедры: преподавателей, ожидается работ всего, загружено (по academic_year отчёта) */
    public DepartmentAnalyticsDto getDepartmentAnalytics(String academicYear) {

        String sql = """
            WITH teachers_in_scope AS (
              SELECT DISTINCT LOWER(TRIM(t.last_name)) AS ln
              FROM public.teaching_load_csv t
              WHERE t.department_name ILIKE ? AND t.last_name IS NOT NULL AND TRIM(t.last_name) <> ''
            ),
            tl AS (
              SELECT DISTINCT t.last_name, t.plan_row_id, NULLIF(ltrim(btrim(t.group_name), '0'), '') AS group_norm
              FROM public.teaching_load_csv t
              WHERE t.department_name ILIKE ? AND t.last_name IS NOT NULL
            ),
            stud AS (
              SELECT NULLIF(ltrim(btrim(s.group_name), '0'), '') AS group_norm, COUNT(*)::int AS students_cnt
              FROM public.students s WHERE s.status IS DISTINCT FROM 'Отчислен' GROUP BY 1
            ),
            controls AS (
              SELECT plan_row_id, GREATEST(COALESCE(COUNT(DISTINCT control_text), 0), 1)::bigint AS controls_cnt
              FROM public.control_schedule GROUP BY plan_row_id
            ),
            expected_all AS (
              SELECT SUM(COALESCE(stud.students_cnt, 0) * COALESCE(c.controls_cnt, 1))::bigint AS expected_cnt
              FROM tl LEFT JOIN stud ON stud.group_norm = tl.group_norm
              LEFT JOIN controls c ON c.plan_row_id = tl.plan_row_id
            ),
            ranked AS (
              SELECT r.id, ROW_NUMBER() OVER (
                PARTITION BY r.plan_row_id, r.student_id, lower(btrim(r.group_name)),
                  lower(btrim(r.teacher_last_name)), lower(COALESCE(btrim(r.control_type),'')), COALESCE(btrim(r.academic_year),'')
                ORDER BY r.version DESC, r.id DESC
              ) AS rn
              FROM public.student_reports r
              WHERE btrim(COALESCE(r.academic_year, '')) = ?
                AND LOWER(TRIM(r.teacher_last_name)) IN (SELECT ln FROM teachers_in_scope)
            )
            SELECT (SELECT COUNT(*) FROM teachers_in_scope)::int AS total_teachers,
              COALESCE((SELECT expected_cnt FROM expected_all), 0)::bigint AS expected_cnt,
              (SELECT COUNT(*) FROM ranked WHERE rn = 1)::bigint AS total_works
            """;
        try {
            return jdbc.queryForObject(sql, (rs, i) -> {
                DepartmentAnalyticsDto dto = new DepartmentAnalyticsDto();
                dto.setTotalTeachers(rs.getInt("total_teachers"));
                dto.setExpectedCount(rs.getLong("expected_cnt"));
                dto.setTotalWorks(rs.getLong("total_works"));
                return dto;
            }, dept(), dept(), academicYear);
        } catch (Exception e) {
            DepartmentAnalyticsDto dto = new DepartmentAnalyticsDto();
            dto.setTotalTeachers(0);
            dto.setExpectedCount(0);
            dto.setTotalWorks(0);
            return dto;
        }
    }

    /** Статистика по преподавателям: ФИО, дисциплины, всего работ (ожидается), загружено, % по шаблону */
    public List<TeacherSummaryRowDto> getTeacherSummary(String academicYear) {

        String sql = """
            WITH tl AS (
              SELECT DISTINCT t.last_name, t.first_name, t.plan_row_id, NULLIF(ltrim(btrim(t.group_name), '0'), '') AS group_norm
              FROM public.teaching_load_csv t
              WHERE t.department_name ILIKE ? AND t.last_name IS NOT NULL AND TRIM(t.last_name) <> ''
            ),
            stud AS (
              SELECT NULLIF(ltrim(btrim(s.group_name), '0'), '') AS group_norm, COUNT(*)::int AS students_cnt
              FROM public.students s
              WHERE s.status IS DISTINCT FROM 'Отчислен'
              GROUP BY 1
            ),
            controls AS (
              SELECT plan_row_id, GREATEST(COALESCE(COUNT(DISTINCT control_text), 0), 1)::bigint AS controls_cnt
              FROM public.control_schedule GROUP BY plan_row_id
            ),
            expected_agg AS (
              SELECT tl.last_name, tl.first_name,
                SUM(COALESCE(stud.students_cnt, 0) * COALESCE(c.controls_cnt, 1))::bigint AS expected_cnt
              FROM tl
              LEFT JOIN stud ON stud.group_norm = tl.group_norm
              LEFT JOIN controls c ON c.plan_row_id = tl.plan_row_id
              GROUP BY tl.last_name, tl.first_name
            ),
            teachers_scope AS (
              SELECT ea.last_name, ea.first_name, ea.expected_cnt,
                (SELECT COUNT(DISTINCT t2.discipline_name) FROM public.teaching_load_csv t2
                 WHERE LOWER(TRIM(t2.last_name)) = LOWER(TRIM(ea.last_name)) AND t2.department_name ILIKE ?) AS disciplines_count
              FROM expected_agg ea
            ),
            ranked_reports AS (
              SELECT r.teacher_last_name,
                ROW_NUMBER() OVER (
                  PARTITION BY r.plan_row_id, r.student_id, lower(btrim(r.group_name)),
                    lower(btrim(r.teacher_last_name)), lower(COALESCE(btrim(r.control_type),'')),
                    COALESCE(btrim(r.academic_year),'')
                  ORDER BY r.version DESC, r.id DESC
                ) AS rn
              FROM public.student_reports r
              WHERE EXISTS (SELECT 1 FROM public.teaching_load_csv t WHERE LOWER(TRIM(t.last_name)) = LOWER(TRIM(r.teacher_last_name)) AND t.department_name ILIKE ?)
                AND btrim(COALESCE(r.academic_year, '')) = ?
            ),
            reports_agg AS (
              SELECT teacher_last_name, COUNT(*) AS uploaded_cnt
              FROM ranked_reports WHERE rn = 1
              GROUP BY teacher_last_name
            )
            SELECT TRIM(ts.last_name) || ' ' || COALESCE(LEFT(ts.first_name, 1), '') || '.' AS teacher_fio,
              TRIM(ts.last_name) AS teacher_last_name,
              ts.disciplines_count, COALESCE(ts.expected_cnt, 0)::bigint AS expected_cnt,
              COALESCE(ra.uploaded_cnt, 0)::bigint AS uploaded_cnt
            FROM teachers_scope ts
            LEFT JOIN reports_agg ra ON LOWER(TRIM(ra.teacher_last_name)) = LOWER(TRIM(ts.last_name))
            ORDER BY ts.last_name
            """;
        try {
            return jdbc.query(sql, (rs, i) -> {
                TeacherSummaryRowDto dto = new TeacherSummaryRowDto();
                dto.setTeacherFio(rs.getString("teacher_fio"));
                dto.setTeacherLastName(rs.getString("teacher_last_name"));
                dto.setDisciplinesCount(rs.getInt("disciplines_count"));
                dto.setExpectedCount(rs.getLong("expected_cnt"));
                dto.setUploadedCount(rs.getLong("uploaded_cnt"));
                return dto;
            }, dept(), dept(), dept(), academicYear);
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Нагрузка по преподавателям (bar chart) */
    public List<TeacherWorkloadDto> getTeacherWorkload(LocalDate dateFrom, LocalDate dateTo) {
        Date df = dateFrom != null ? Date.valueOf(dateFrom) : Date.valueOf("1900-01-01");
        Date dt = dateTo != null ? Date.valueOf(dateTo) : Date.valueOf("2100-12-31");

        String sql = """
            WITH base AS (
              SELECT r.teacher_last_name,
                ROW_NUMBER() OVER (
                  PARTITION BY r.plan_row_id, r.student_id, lower(btrim(r.group_name)),
                    lower(btrim(r.teacher_last_name)), lower(COALESCE(btrim(r.control_type),'')),
                    COALESCE(btrim(r.academic_year),'')
                  ORDER BY r.version DESC, r.upload_date DESC NULLS LAST
                ) AS rn
              FROM public.student_reports r
              WHERE r.upload_date >= ? AND r.upload_date <= ?
            )
            SELECT TRIM(teacher_last_name) AS teacher, COUNT(*)::bigint AS workload
            FROM base WHERE rn = 1 AND teacher_last_name IN (SELECT last_name FROM public.teaching_load_csv WHERE department_name ILIKE ?)
            GROUP BY teacher_last_name ORDER BY workload DESC
            """;
        try {
            return jdbc.query(sql, (rs, i) -> {
                TeacherWorkloadDto dto = new TeacherWorkloadDto();
                dto.setTeacher(rs.getString("teacher"));
                dto.setWorkload(rs.getLong("workload"));
                return dto;
            }, df, dt, dept());
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Статистика по дисциплинам кафедры — группы, студенты, X/Y, прогресс, ср. % (все преподаватели кафедры) */
    public List<DisciplineTableRowDto> getDepartmentDisciplineStats(String academicYear) {

        String sql = """
            WITH tl AS (
              SELECT DISTINCT t.plan_row_id, t.discipline_name, NULLIF(ltrim(btrim(t.group_name), '0'), '') AS group_norm
              FROM public.teaching_load_csv t
              WHERE t.department_name ILIKE ?
            ),
            stud AS (
              SELECT NULLIF(ltrim(btrim(s.group_name), '0'), '') AS group_norm, COUNT(*)::int AS students_cnt
              FROM public.students s
              WHERE s.status IS DISTINCT FROM 'Отчислен'
              GROUP BY 1
            ),
            controls AS (
              SELECT plan_row_id, GREATEST(COALESCE(COUNT(DISTINCT control_text), 0), 1)::bigint AS controls_cnt
              FROM public.control_schedule GROUP BY plan_row_id
            ),
            lr AS (
              SELECT * FROM (
                SELECT sr.*, ROW_NUMBER() OVER (
                  PARTITION BY sr.plan_row_id, sr.student_id, lower(btrim(sr.group_name)),
                    lower(btrim(sr.teacher_last_name)), lower(COALESCE(btrim(sr.control_type),'')),
                    COALESCE(btrim(sr.academic_year),'')
                  ORDER BY sr.version DESC, sr.id DESC
                ) rn
                FROM public.student_reports sr
                WHERE btrim(COALESCE(sr.academic_year, '')) = ?
                  AND EXISTS (SELECT 1 FROM public.teaching_load_csv t2 WHERE LOWER(TRIM(t2.last_name)) = LOWER(TRIM(sr.teacher_last_name)) AND t2.department_name ILIKE ?)
              ) t WHERE rn = 1
            ),
            by_group AS (
              SELECT tl.plan_row_id, tl.group_norm, tl.discipline_name,
                COALESCE(MAX(stud.students_cnt), 0)::int AS students_cnt,
                COALESCE(MAX(controls.controls_cnt), 1)::bigint AS controls_cnt,
                COUNT(lr.id)::bigint AS uploaded
              FROM tl
              LEFT JOIN stud ON stud.group_norm = tl.group_norm
              LEFT JOIN controls ON controls.plan_row_id = tl.plan_row_id
              LEFT JOIN lr ON lr.plan_row_id = tl.plan_row_id AND NULLIF(ltrim(btrim(lr.group_name), '0'), '') = tl.group_norm
              GROUP BY tl.plan_row_id, tl.group_norm, tl.discipline_name
            )
            SELECT bg.discipline_name,
              COUNT(DISTINCT bg.group_norm)::int AS groups_cnt,
              SUM(bg.students_cnt)::int AS students_cnt,
              SUM(bg.uploaded)::bigint AS uploaded,
              SUM(bg.students_cnt * bg.controls_cnt)::bigint AS expected
            FROM by_group bg
            GROUP BY bg.discipline_name
            ORDER BY bg.discipline_name
            """;
        try {
            return jdbc.query(sql, (rs, i) -> {
                DisciplineTableRowDto dto = new DisciplineTableRowDto();
                dto.setDisciplineName(rs.getString("discipline_name"));
                dto.setGroupsCount(rs.getInt("groups_cnt"));
                dto.setStudentsCount(rs.getInt("students_cnt"));
                dto.setUploadedCount(rs.getLong("uploaded"));
                dto.setExpectedCount(rs.getLong("expected"));
                return dto;
            }, dept(), academicYear, dept());
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Кафедра: агрегат по курсу и семестру — ожидается / загружено */
    public List<SemesterStatsRowDto> getDepartmentSemesterStats(String academicYear) {

        String sql = """
            WITH tl AS (
              SELECT DISTINCT t.plan_row_id, t.discipline_name, t.course, t.semester,
                NULLIF(ltrim(btrim(t.group_name), '0'), '') AS group_norm
              FROM public.teaching_load_csv t
              WHERE t.department_name ILIKE ?
            ),
            stud AS (
              SELECT NULLIF(ltrim(btrim(s.group_name), '0'), '') AS group_norm, COUNT(*)::int AS students_cnt
              FROM public.students s
              WHERE s.status IS DISTINCT FROM 'Отчислен'
              GROUP BY 1
            ),
            controls AS (
              SELECT plan_row_id, GREATEST(COALESCE(COUNT(DISTINCT control_text), 0), 1)::bigint AS controls_cnt
              FROM public.control_schedule GROUP BY plan_row_id
            ),
            lr AS (
              SELECT * FROM (
                SELECT sr.*, ROW_NUMBER() OVER (
                  PARTITION BY sr.plan_row_id, sr.student_id, lower(btrim(sr.group_name)),
                    lower(btrim(sr.teacher_last_name)), lower(COALESCE(btrim(sr.control_type),'')),
                    COALESCE(btrim(sr.academic_year),'')
                  ORDER BY sr.version DESC, sr.id DESC
                ) rn
                FROM public.student_reports sr
                WHERE btrim(COALESCE(sr.academic_year, '')) = ?
                  AND EXISTS (SELECT 1 FROM public.teaching_load_csv t2 WHERE LOWER(TRIM(t2.last_name)) = LOWER(TRIM(sr.teacher_last_name)) AND t2.department_name ILIKE ?)
              ) t WHERE rn = 1
            ),
            by_group AS (
              SELECT tl.plan_row_id, tl.group_norm, tl.course, tl.semester,
                COALESCE(MAX(stud.students_cnt), 0)::int AS students_cnt,
                COALESCE(MAX(controls.controls_cnt), 1)::bigint AS controls_cnt,
                COUNT(lr.id)::bigint AS uploaded
              FROM tl
              LEFT JOIN stud ON stud.group_norm = tl.group_norm
              LEFT JOIN controls ON controls.plan_row_id = tl.plan_row_id
              LEFT JOIN lr ON lr.plan_row_id = tl.plan_row_id AND NULLIF(ltrim(btrim(lr.group_name), '0'), '') = tl.group_norm
              GROUP BY tl.plan_row_id, tl.group_norm, tl.course, tl.semester
            )
            SELECT bg.course, bg.semester,
              COUNT(DISTINCT bg.group_norm)::int AS groups_cnt,
              SUM(bg.students_cnt)::int AS students_cnt,
              SUM(bg.uploaded)::bigint AS uploaded,
              SUM(bg.students_cnt * bg.controls_cnt)::bigint AS expected
            FROM by_group bg
            GROUP BY bg.course, bg.semester
            ORDER BY bg.course NULLS LAST, bg.semester NULLS LAST
            """;
        try {
            return jdbc.query(sql, (rs, i) -> {
                SemesterStatsRowDto dto = new SemesterStatsRowDto();
                dto.setCourse((Integer) rs.getObject("course"));
                dto.setSemester((Integer) rs.getObject("semester"));
                dto.setGroupsCount(rs.getInt("groups_cnt"));
                dto.setStudentsCount(rs.getInt("students_cnt"));
                dto.setUploadedCount(rs.getLong("uploaded"));
                dto.setExpectedCount(rs.getLong("expected"));
                return dto;
            }, dept(), academicYear, dept());
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Преподаватель: агрегат по курсу и семестру — ожидается / загружено */
    public List<SemesterStatsRowDto> getTeacherSemesterStats(String academicYear, String teacherLastName) {
        String ln = teacherLastName == null ? "" : teacherLastName.trim();

        String sql = """
            WITH tl AS (
              SELECT DISTINCT t.plan_row_id, t.discipline_name, t.course, t.semester,
                NULLIF(ltrim(btrim(t.group_name), '0'), '') AS group_norm
              FROM public.teaching_load_csv t
              WHERE LOWER(TRIM(t.last_name)) = LOWER(TRIM(?)) AND t.department_name ILIKE ?
            ),
            stud AS (
              SELECT NULLIF(ltrim(btrim(s.group_name), '0'), '') AS group_norm, COUNT(*)::int AS students_cnt
              FROM public.students s
              WHERE s.status IS DISTINCT FROM 'Отчислен'
              GROUP BY 1
            ),
            controls AS (
              SELECT plan_row_id, GREATEST(COALESCE(COUNT(DISTINCT control_text), 0), 1)::bigint AS controls_cnt
              FROM public.control_schedule GROUP BY plan_row_id
            ),
            lr AS (
              SELECT * FROM (
                SELECT sr.*, ROW_NUMBER() OVER (
                  PARTITION BY sr.plan_row_id, sr.student_id, lower(btrim(sr.group_name)),
                    lower(btrim(sr.teacher_last_name)), lower(COALESCE(btrim(sr.control_type),'')),
                    COALESCE(btrim(sr.academic_year),'')
                  ORDER BY sr.version DESC, sr.id DESC
                ) rn
                FROM public.student_reports sr
                WHERE LOWER(TRIM(sr.teacher_last_name)) = LOWER(TRIM(?))
                  AND btrim(COALESCE(sr.academic_year, '')) = ?
              ) t WHERE rn = 1
            ),
            by_group AS (
              SELECT tl.plan_row_id, tl.group_norm, tl.course, tl.semester,
                COALESCE(MAX(stud.students_cnt), 0)::int AS students_cnt,
                COALESCE(MAX(controls.controls_cnt), 1)::bigint AS controls_cnt,
                COUNT(lr.id)::bigint AS uploaded
              FROM tl
              LEFT JOIN stud ON stud.group_norm = tl.group_norm
              LEFT JOIN controls ON controls.plan_row_id = tl.plan_row_id
              LEFT JOIN lr ON lr.plan_row_id = tl.plan_row_id AND NULLIF(ltrim(btrim(lr.group_name), '0'), '') = tl.group_norm
              GROUP BY tl.plan_row_id, tl.group_norm, tl.course, tl.semester
            )
            SELECT bg.course, bg.semester,
              COUNT(DISTINCT bg.group_norm)::int AS groups_cnt,
              SUM(bg.students_cnt)::int AS students_cnt,
              SUM(bg.uploaded)::bigint AS uploaded,
              SUM(bg.students_cnt * bg.controls_cnt)::bigint AS expected
            FROM by_group bg
            GROUP BY bg.course, bg.semester
            ORDER BY bg.course NULLS LAST, bg.semester NULLS LAST
            """;
        try {
            return jdbc.query(sql, (rs, i) -> {
                SemesterStatsRowDto dto = new SemesterStatsRowDto();
                dto.setCourse((Integer) rs.getObject("course"));
                dto.setSemester((Integer) rs.getObject("semester"));
                dto.setGroupsCount(rs.getInt("groups_cnt"));
                dto.setStudentsCount(rs.getInt("students_cnt"));
                dto.setUploadedCount(rs.getLong("uploaded"));
                dto.setExpectedCount(rs.getLong("expected"));
                return dto;
            }, ln, dept(), ln, academicYear);
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Статистика по дисциплинам кафедры с ФИО преподавателя и полной информацией о дисциплине (курс, семестр, уровень/форма образования, часы, виды контроля). */
    public List<DisciplineWithTeacherRowDto> getDepartmentDisciplineStatsWithTeachers(String academicYear) {

        String sql = """
            WITH tl AS (
              SELECT DISTINCT t.plan_row_id, t.discipline_name, t.course, t.semester,
                NULLIF(ltrim(btrim(t.group_name), '0'), '') AS group_norm,
                TRIM(COALESCE(TRIM(t.last_name),'') || ' ' || COALESCE(TRIM(t.first_name),'') || ' ' || COALESCE(TRIM(t.patronymic),'')) AS teacher_fio,
                TRIM(t.last_name) AS teacher_last_name,
                LOWER(TRIM(t.last_name)) AS teacher_ln
              FROM public.teaching_load_csv t
              WHERE t.department_name ILIKE ?
            ),
            plan_meta AS (
              SELECT t.plan_row_id, LOWER(TRIM(t.last_name)) AS teacher_ln,
                COALESCE(SUM(CASE WHEN t.activity_type ILIKE 'Лек%' THEN t.hours ELSE 0 END), 0)::numeric AS lecture_hours,
                COALESCE(SUM(CASE WHEN t.activity_type ILIKE 'Пр%' THEN t.hours ELSE 0 END), 0)::numeric AS practice_hours,
                BOOL_OR(t.has_exam) AS has_exam, BOOL_OR(t.has_pass) AS has_pass, BOOL_OR(t.has_pass_mark) AS has_pass_mark
              FROM public.teaching_load_csv t
              WHERE t.department_name ILIKE ?
              GROUP BY t.plan_row_id, t.last_name, t.first_name, t.patronymic
            ),
            stud AS (
              SELECT NULLIF(ltrim(btrim(s.group_name), '0'), '') AS group_norm, COUNT(*)::int AS students_cnt
              FROM public.students s
              WHERE s.status IS DISTINCT FROM 'Отчислен'
              GROUP BY 1
            ),
            controls AS (
              SELECT plan_row_id, GREATEST(COALESCE(COUNT(DISTINCT control_text), 0), 1)::bigint AS controls_cnt
              FROM public.control_schedule GROUP BY plan_row_id
            ),
            lr AS (
              SELECT * FROM (
                SELECT sr.*, ROW_NUMBER() OVER (
                  PARTITION BY sr.plan_row_id, sr.student_id, lower(btrim(sr.group_name)),
                    lower(btrim(sr.teacher_last_name)), lower(COALESCE(btrim(sr.control_type),'')),
                    COALESCE(btrim(sr.academic_year),'')
                  ORDER BY sr.version DESC, sr.id DESC
                ) rn
                FROM public.student_reports sr
                WHERE btrim(COALESCE(sr.academic_year, '')) = ?
                  AND EXISTS (SELECT 1 FROM public.teaching_load_csv t2 WHERE LOWER(TRIM(t2.last_name)) = LOWER(TRIM(sr.teacher_last_name)) AND t2.department_name ILIKE ?)
              ) t WHERE rn = 1
            ),
            by_group AS (
              SELECT tl.plan_row_id, tl.group_norm, tl.discipline_name, tl.teacher_fio, tl.teacher_last_name, tl.teacher_ln, tl.course, tl.semester,
                COALESCE(MAX(stud.students_cnt), 0)::int AS students_cnt,
                COALESCE(MAX(controls.controls_cnt), 1)::bigint AS controls_cnt,
                COUNT(lr.id)::bigint AS uploaded
              FROM tl
              LEFT JOIN stud ON stud.group_norm = tl.group_norm
              LEFT JOIN controls ON controls.plan_row_id = tl.plan_row_id
              LEFT JOIN lr ON lr.plan_row_id = tl.plan_row_id AND NULLIF(ltrim(btrim(lr.group_name), '0'), '') = tl.group_norm
                AND LOWER(TRIM(lr.teacher_last_name)) = tl.teacher_ln
              GROUP BY tl.plan_row_id, tl.group_norm, tl.discipline_name, tl.teacher_fio, tl.teacher_last_name, tl.teacher_ln, tl.course, tl.semester
            )
            SELECT MIN(bg.plan_row_id)::int AS plan_row_id, bg.discipline_name, bg.teacher_fio, MAX(bg.teacher_last_name) AS teacher_last_name, bg.course, bg.semester,
              TRIM(BOTH ', ' FROM STRING_AGG(DISTINCT NULLIF(TRIM(g.education_level), ''), ', ')) AS education_level,
              TRIM(BOTH ', ' FROM STRING_AGG(DISTINCT NULLIF(TRIM(g.education_form), ''), ', ')) AS education_form,
              MAX(pm.lecture_hours) AS lecture_hours, MAX(pm.practice_hours) AS practice_hours,
              BOOL_OR(pm.has_exam) AS has_exam, BOOL_OR(pm.has_pass) AS has_pass, BOOL_OR(pm.has_pass_mark) AS has_pass_mark,
              COALESCE(ARRAY_AGG(DISTINCT bg.group_norm ORDER BY bg.group_norm) FILTER (WHERE bg.group_norm IS NOT NULL), ARRAY[]::text[]) AS groups,
              COUNT(DISTINCT bg.group_norm)::int AS groups_cnt,
              SUM(bg.students_cnt)::int AS students_cnt,
              SUM(bg.uploaded)::bigint AS uploaded,
              SUM(bg.students_cnt * bg.controls_cnt)::bigint AS expected
            FROM by_group bg
            LEFT JOIN plan_meta pm ON pm.plan_row_id = bg.plan_row_id AND pm.teacher_ln = bg.teacher_ln
            LEFT JOIN public.groups g ON NULLIF(ltrim(btrim(g.name), '0'), '') = bg.group_norm AND g.course = bg.course
            GROUP BY bg.discipline_name, bg.teacher_fio, bg.course, bg.semester
            ORDER BY bg.discipline_name, bg.teacher_fio, bg.course, bg.semester
            """;
        try {
            return jdbc.query(sql, (rs, i) -> {
                DisciplineWithTeacherRowDto dto = new DisciplineWithTeacherRowDto();
                dto.setPlanRowId((Integer) rs.getObject("plan_row_id"));
                dto.setDisciplineName(rs.getString("discipline_name"));
                dto.setTeacherFio(rs.getString("teacher_fio"));
                dto.setTeacherLastName(rs.getString("teacher_last_name"));
                dto.setCourse((Integer) rs.getObject("course"));
                dto.setSemester((Integer) rs.getObject("semester"));
                dto.setEducationLevel(rs.getString("education_level"));
                dto.setEducationForm(rs.getString("education_form"));
                dto.setLectureHours(rs.getBigDecimal("lecture_hours"));
                dto.setPracticeHours(rs.getBigDecimal("practice_hours"));
                dto.setHasExam((Boolean) rs.getObject("has_exam"));
                dto.setHasPass((Boolean) rs.getObject("has_pass"));
                dto.setHasPassMark((Boolean) rs.getObject("has_pass_mark"));
                java.sql.Array groupsArray = rs.getArray("groups");
                dto.setGroups(groupsArray == null ? List.of() : Arrays.asList((String[]) groupsArray.getArray()));
                dto.setGroupsCount(rs.getInt("groups_cnt"));
                dto.setStudentsCount(rs.getInt("students_cnt"));
                dto.setUploadedCount(rs.getLong("uploaded"));
                dto.setExpectedCount(rs.getLong("expected"));
                return dto;
            }, dept(), dept(), academicYear, dept());
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Распределение по дисциплинам — всего, с проверкой, прошли, ср. % */
    public List<DisciplineStatsDto> getDepartmentByDisciplines(LocalDate dateFrom, LocalDate dateTo) {
        Date df = dateFrom != null ? Date.valueOf(dateFrom) : Date.valueOf("1900-01-01");
        Date dt = dateTo != null ? Date.valueOf(dateTo) : Date.valueOf("2100-12-31");

        String sql = """
            WITH base AS (
              SELECT COALESCE(tl.discipline_name, '') AS discipline_name, r.check_percent, vr.percent AS vr_percent, vr.valid,
                ROW_NUMBER() OVER (
                  PARTITION BY r.plan_row_id, r.student_id, lower(btrim(r.group_name)),
                    lower(btrim(r.teacher_last_name)), lower(COALESCE(btrim(r.control_type),'')),
                    COALESCE(btrim(r.academic_year),'')
                  ORDER BY r.version DESC, r.id DESC
                ) AS rn
              FROM public.student_reports r
              LEFT JOIN LATERAL (SELECT t.discipline_name FROM public.teaching_load_csv t WHERE t.plan_row_id = r.plan_row_id AND t.discipline_name IS NOT NULL LIMIT 1) tl ON TRUE
              LEFT JOIN LATERAL (SELECT percent, valid FROM public.validation_results vr2 WHERE vr2.report_id = r.id ORDER BY vr2.checked_at DESC LIMIT 1) vr ON TRUE
              WHERE r.upload_date >= ? AND r.upload_date <= ?
            )
            SELECT discipline_name, COUNT(*)::bigint AS total,
              COUNT(*) FILTER (WHERE vr_percent IS NOT NULL OR valid IS NOT NULL)::bigint AS with_validation,
              COUNT(*) FILTER (WHERE valid = true)::bigint AS passed,
              ROUND(AVG(COALESCE(vr_percent, check_percent)) FILTER (WHERE COALESCE(vr_percent, check_percent) IS NOT NULL)::numeric, 0)::int AS avg_percent
            FROM base WHERE rn = 1
            GROUP BY discipline_name ORDER BY total DESC
            """;
        try {
            return jdbc.query(sql, (rs, i) -> {
                DisciplineStatsDto dto = new DisciplineStatsDto();
                dto.setDisciplineName(rs.getString("discipline_name"));
                dto.setTotal(rs.getLong("total"));
                dto.setWithValidation(rs.getLong("with_validation"));
                dto.setPassed(rs.getLong("passed"));
                dto.setAvgPercent((Integer) rs.getObject("avg_percent"));
                return dto;
            }, df, dt);
        } catch (Exception e) {
            return List.of();
        }
    }

    /** По виду контроля — всего, с проверкой, прошли, ср. % */
    public List<ControlTypeStatsDto> getByControlType(LocalDate dateFrom, LocalDate dateTo, String teacherLastName) {
        Date df = dateFrom != null ? Date.valueOf(dateFrom) : Date.valueOf("1900-01-01");
        Date dt = dateTo != null ? Date.valueOf(dateTo) : Date.valueOf("2100-12-31");

        String teacherFilter = teacherLastName != null && !teacherLastName.isBlank()
            ? " AND LOWER(TRIM(r.teacher_last_name)) = LOWER(TRIM(?))" : "";

        String sql = """
            WITH base AS (
              SELECT COALESCE(NULLIF(TRIM(r.control_type), ''), '—') AS control_type, r.check_percent, vr.percent AS vr_percent, vr.valid,
                ROW_NUMBER() OVER (
                  PARTITION BY r.plan_row_id, r.student_id, lower(btrim(r.group_name)),
                    lower(btrim(r.teacher_last_name)), lower(COALESCE(btrim(r.control_type),'')),
                    COALESCE(btrim(r.academic_year),'')
                  ORDER BY r.version DESC, r.id DESC
                ) AS rn
              FROM public.student_reports r
              LEFT JOIN LATERAL (SELECT percent, valid FROM public.validation_results vr2 WHERE vr2.report_id = r.id ORDER BY vr2.checked_at DESC LIMIT 1) vr ON TRUE
              WHERE r.upload_date >= ? AND r.upload_date <= ?
            """ + teacherFilter + """
            )
            SELECT control_type, COUNT(*)::bigint AS total,
              COUNT(*) FILTER (WHERE vr_percent IS NOT NULL OR valid IS NOT NULL)::bigint AS with_validation,
              COUNT(*) FILTER (WHERE valid = true)::bigint AS passed,
              ROUND(AVG(COALESCE(vr_percent, check_percent)) FILTER (WHERE COALESCE(vr_percent, check_percent) IS NOT NULL)::numeric, 0)::int AS avg_percent
            FROM base WHERE rn = 1
            GROUP BY control_type ORDER BY total DESC
            """;
        try {
            if (teacherLastName != null && !teacherLastName.isBlank()) {
                return jdbc.query(sql, (rs, i) -> {
                    ControlTypeStatsDto dto = new ControlTypeStatsDto();
                    dto.setControlType(rs.getString("control_type"));
                    dto.setTotal(rs.getLong("total"));
                    dto.setWithValidation(rs.getLong("with_validation"));
                    dto.setPassed(rs.getLong("passed"));
                    dto.setAvgPercent((Integer) rs.getObject("avg_percent"));
                    return dto;
                }, df, dt, teacherLastName.trim());
            } else {
                return jdbc.query(sql.replace(teacherFilter, ""), (rs, i) -> {
                    ControlTypeStatsDto dto = new ControlTypeStatsDto();
                    dto.setControlType(rs.getString("control_type"));
                    dto.setTotal(rs.getLong("total"));
                    dto.setWithValidation(rs.getLong("with_validation"));
                    dto.setPassed(rs.getLong("passed"));
                    dto.setAvgPercent((Integer) rs.getObject("avg_percent"));
                    return dto;
                }, df, dt);
            }
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Динамика по месяцам — загрузки и проверка */
    public List<MonthlyStatsDto> getMonthlyStats(LocalDate dateFrom, LocalDate dateTo, String teacherLastName) {
        Date df = dateFrom != null ? Date.valueOf(dateFrom) : Date.valueOf(LocalDate.now().minusMonths(6));
        Date dt = dateTo != null ? Date.valueOf(dateTo) : Date.valueOf(LocalDate.now());

        String teacherFilter = teacherLastName != null && !teacherLastName.isBlank()
            ? " AND LOWER(TRIM(r.teacher_last_name)) = LOWER(TRIM(?))" : "";

        String sql = """
            WITH months AS (
              SELECT date_trunc('month', d)::date AS month_start
              FROM generate_series(?::date, ?::date, '1 month'::interval) AS d
            ),
            base AS (
              SELECT date_trunc('month', r.upload_date)::date AS month_start,
                r.check_percent, vr.percent AS vr_percent, vr.valid,
                ROW_NUMBER() OVER (
                  PARTITION BY r.plan_row_id, r.student_id, lower(btrim(r.group_name)),
                    lower(btrim(r.teacher_last_name)), lower(COALESCE(btrim(r.control_type),'')),
                    COALESCE(btrim(r.academic_year),'')
                  ORDER BY r.version DESC, r.id DESC
                ) AS rn
              FROM public.student_reports r
              LEFT JOIN LATERAL (SELECT percent, valid FROM public.validation_results vr2 WHERE vr2.report_id = r.id ORDER BY vr2.checked_at DESC LIMIT 1) vr ON TRUE
              WHERE r.upload_date >= ? AND r.upload_date <= ?
            """ + teacherFilter + """
            ),
            agg AS (
              SELECT month_start, COUNT(*)::bigint AS total,
                COUNT(*) FILTER (WHERE vr_percent IS NOT NULL OR valid IS NOT NULL)::bigint AS with_validation,
                COUNT(*) FILTER (WHERE valid = true)::bigint AS passed,
                ROUND(AVG(COALESCE(vr_percent, check_percent)) FILTER (WHERE COALESCE(vr_percent, check_percent) IS NOT NULL)::numeric, 0)::int AS avg_percent
              FROM base WHERE rn = 1
              GROUP BY month_start
            )
            SELECT to_char(m.month_start, 'YYYY-MM') AS month,
              COALESCE(a.total, 0)::bigint AS total,
              COALESCE(a.with_validation, 0)::bigint AS with_validation,
              COALESCE(a.passed, 0)::bigint AS passed,
              a.avg_percent
            FROM months m
            LEFT JOIN agg a ON a.month_start = m.month_start
            ORDER BY m.month_start
            """;
        try {
            if (teacherLastName != null && !teacherLastName.isBlank()) {
                return jdbc.query(sql, (rs, i) -> {
                    MonthlyStatsDto dto = new MonthlyStatsDto();
                    dto.setMonth(rs.getString("month"));
                    dto.setTotal(rs.getLong("total"));
                    dto.setWithValidation(rs.getLong("with_validation"));
                    dto.setPassed(rs.getLong("passed"));
                    dto.setAvgPercent((Integer) rs.getObject("avg_percent"));
                    return dto;
                }, df, dt, df, dt, teacherLastName.trim());
            } else {
                return jdbc.query(sql.replace(teacherFilter, ""), (rs, i) -> {
                    MonthlyStatsDto dto = new MonthlyStatsDto();
                    dto.setMonth(rs.getString("month"));
                    dto.setTotal(rs.getLong("total"));
                    dto.setWithValidation(rs.getLong("with_validation"));
                    dto.setPassed(rs.getLong("passed"));
                    dto.setAvgPercent((Integer) rs.getObject("avg_percent"));
                    return dto;
                }, df, dt, df, dt);
            }
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Динамика загрузок: загружено (по upload_date) vs прогнано шаблоном (по checked_at) по месяцам */
    public List<UploadDynamicsDto> getUploadDynamics(LocalDate dateFrom, LocalDate dateTo, String teacherLastName) {
        Date df = dateFrom != null ? Date.valueOf(dateFrom) : Date.valueOf(LocalDate.now().minusMonths(6));
        Date dt = dateTo != null ? Date.valueOf(dateTo) : Date.valueOf(LocalDate.now());
        String teacherFilter = teacherLastName != null && !teacherLastName.isBlank()
            ? " AND LOWER(TRIM(sr.teacher_last_name)) = LOWER(TRIM(?))" : "";

        String sql = """
            WITH lr AS (
              SELECT * FROM (
                SELECT sr.*,
                  ROW_NUMBER() OVER (
                    PARTITION BY sr.plan_row_id, sr.student_id, lower(btrim(sr.group_name)),
                      lower(btrim(sr.teacher_last_name)), lower(COALESCE(btrim(sr.control_type),'')),
                      COALESCE(btrim(sr.academic_year),'')
                    ORDER BY sr.version DESC, sr.upload_date DESC NULLS LAST, sr.id DESC
                  ) rn
                FROM public.student_reports sr
                WHERE sr.upload_date >= ? AND sr.upload_date <= ?
            """ + teacherFilter + """
              ) t WHERE rn = 1
            ),
            lv AS (
              SELECT * FROM (
                SELECT vr.*,
                  ROW_NUMBER() OVER (PARTITION BY vr.report_id ORDER BY vr.checked_at DESC, vr.id DESC) rn
                FROM public.validation_results vr
              ) v WHERE rn = 1
            ),
            uploads AS (
              SELECT date_trunc('month', lr.upload_date)::date AS m, COUNT(*)::bigint AS uploaded
              FROM lr GROUP BY 1
            ),
            checks AS (
              SELECT date_trunc('month', lv.checked_at)::date AS m, COUNT(*)::bigint AS checked
              FROM lv JOIN lr ON lr.id = lv.report_id GROUP BY 1
            )
            SELECT to_char(COALESCE(u.m, c.m), 'YYYY-MM') AS month,
              COALESCE(u.uploaded, 0)::bigint AS uploaded,
              COALESCE(c.checked, 0)::bigint AS checked
            FROM uploads u
            FULL JOIN checks c USING (m)
            ORDER BY COALESCE(u.m, c.m)
            """;
        try {
            if (teacherLastName != null && !teacherLastName.isBlank()) {
                return jdbc.query(sql, (rs, i) -> new UploadDynamicsDto(
                    rs.getString("month"),
                    rs.getLong("uploaded"),
                    rs.getLong("checked")
                ), df, dt, teacherLastName.trim());
            } else {
                return jdbc.query(sql.replace(teacherFilter, ""), (rs, i) -> new UploadDynamicsDto(
                    rs.getString("month"),
                    rs.getLong("uploaded"),
                    rs.getLong("checked")
                ), df, dt);
            }
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Распределение соответствия по бакетам 90-100, 70-89, 50-69, <50 */
    public List<ComplianceBucketDto> getComplianceBuckets(LocalDate dateFrom, LocalDate dateTo, String teacherLastName) {
        Date df = dateFrom != null ? Date.valueOf(dateFrom) : Date.valueOf("1900-01-01");
        Date dt = dateTo != null ? Date.valueOf(dateTo) : Date.valueOf("2100-12-31");
        String teacherFilter = teacherLastName != null && !teacherLastName.isBlank()
            ? " AND LOWER(TRIM(sr.teacher_last_name)) = LOWER(TRIM(?))" : "";

        String sql = """
            WITH lr AS (
              SELECT * FROM (
                SELECT sr.*,
                  ROW_NUMBER() OVER (
                    PARTITION BY sr.plan_row_id, sr.student_id, lower(btrim(sr.group_name)),
                      lower(btrim(sr.teacher_last_name)), lower(COALESCE(btrim(sr.control_type),'')),
                      COALESCE(btrim(sr.academic_year),'')
                    ORDER BY sr.version DESC, sr.id DESC
                  ) rn
                FROM public.student_reports sr
                WHERE sr.upload_date >= ? AND sr.upload_date <= ?
            """ + teacherFilter + """
              ) t WHERE rn = 1
            ),
            lv AS (
              SELECT * FROM (
                SELECT vr.*, ROW_NUMBER() OVER (PARTITION BY vr.report_id ORDER BY vr.checked_at DESC, vr.id DESC) rn
                FROM public.validation_results vr
              ) v WHERE rn = 1
            ),
            base AS (
              SELECT COALESCE(lv.percent, lr.check_percent) AS percent
              FROM lr LEFT JOIN lv ON lv.report_id = lr.id
            )
            SELECT CASE
              WHEN percent >= 90 THEN '90-100'
              WHEN percent >= 70 THEN '70-89'
              WHEN percent >= 50 THEN '50-69'
              ELSE '<50'
            END AS bucket, COUNT(*)::bigint AS cnt
            FROM base WHERE percent IS NOT NULL
            GROUP BY 1 ORDER BY 1
            """;
        try {
            if (teacherLastName != null && !teacherLastName.isBlank()) {
                return jdbc.query(sql, (rs, i) -> new ComplianceBucketDto(rs.getString("bucket"), rs.getLong("cnt")),
                    df, dt, teacherLastName.trim());
            } else {
                return jdbc.query(sql.replace(teacherFilter, ""), (rs, i) ->
                    new ComplianceBucketDto(rs.getString("bucket"), rs.getLong("cnt")), df, dt);
            }
        } catch (Exception e) {
            return List.of();
        }
    }

    /** ТОП категорий ошибок из errors_json или warnings_json. useWarnings=true → warnings_json */
    public List<ErrorCategoryDto> getFrequentErrors(LocalDate dateFrom, LocalDate dateTo, String teacherLastName, boolean useWarnings) {
        Date df = dateFrom != null ? Date.valueOf(dateFrom) : Date.valueOf("1900-01-01");
        Date dt = dateTo != null ? Date.valueOf(dateTo) : Date.valueOf("2100-12-31");
        String teacherFilter = teacherLastName != null && !teacherLastName.isBlank()
            ? " AND LOWER(TRIM(sr.teacher_last_name)) = LOWER(TRIM(?))" : "";
        String jsonCol = useWarnings ? "warnings_json" : "errors_json";

        String sql = "WITH lv AS (" +
            "  SELECT vr.report_id, vr." + jsonCol + " AS j " +
            "  FROM (" +
            "    SELECT vr2.*, ROW_NUMBER() OVER (PARTITION BY vr2.report_id ORDER BY vr2.checked_at DESC, vr2.id DESC) rn " +
            "    FROM public.validation_results vr2" +
            "  ) vr " +
            "  JOIN public.student_reports sr ON sr.id = vr.report_id " +
            "  WHERE vr.rn = 1 AND vr." + jsonCol + " IS NOT NULL " +
            "    AND sr.upload_date >= ? AND sr.upload_date <= ? " +
            teacherFilter + " " +
            ") " +
            "SELECT COALESCE(e->>'title', e->>'code', 'Другое') AS category, COUNT(*)::bigint AS cnt " +
            "FROM lv CROSS JOIN LATERAL jsonb_array_elements(lv.j) e " +
            "GROUP BY 1 ORDER BY cnt DESC LIMIT 10";
        try {
            if (teacherLastName != null && !teacherLastName.isBlank()) {
                return jdbc.query(sql, (rs, i) -> new ErrorCategoryDto(rs.getString("category"), rs.getLong("cnt")),
                    df, dt, teacherLastName.trim());
            } else {
                return jdbc.query(sql.replace(teacherFilter, ""), (rs, i) ->
                    new ErrorCategoryDto(rs.getString("category"), rs.getLong("cnt")), df, dt);
            }
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Статистика по группам: группа, дисциплина, студенты, загружено X/Y, ср. %, критич. ошибки, предупреждения */
    public List<GroupStatsRowDto> getGroupStatsRows(LocalDate dateFrom, LocalDate dateTo, String teacherLastName) {
        Date df = dateFrom != null ? Date.valueOf(dateFrom) : Date.valueOf("1900-01-01");
        Date dt = dateTo != null ? Date.valueOf(dateTo) : Date.valueOf("2100-12-31");
        String ln = teacherLastName == null ? "" : teacherLastName.trim();

        String sql = """
            WITH tl AS (
              SELECT DISTINCT t.plan_row_id, t.discipline_name, NULLIF(ltrim(btrim(t.group_name), '0'), '') AS group_norm
              FROM public.teaching_load_csv t
              WHERE LOWER(TRIM(t.last_name)) = LOWER(TRIM(?)) AND t.department_name ILIKE ?
            ),
            stud AS (
              SELECT NULLIF(ltrim(btrim(s.group_name), '0'), '') AS group_norm, COUNT(*)::int AS students_cnt
              FROM public.students s
              WHERE s.status IS DISTINCT FROM 'Отчислен'
              GROUP BY 1
            ),
            controls AS (
              SELECT plan_row_id, GREATEST(COALESCE(COUNT(DISTINCT control_text), 0), 1)::bigint AS controls_cnt
              FROM public.control_schedule GROUP BY plan_row_id
            ),
            lr AS (
              SELECT * FROM (
                SELECT sr.*, ROW_NUMBER() OVER (
                  PARTITION BY sr.plan_row_id, sr.student_id, lower(btrim(sr.group_name)),
                    lower(btrim(sr.teacher_last_name)), lower(COALESCE(btrim(sr.control_type),'')),
                    COALESCE(btrim(sr.academic_year),'')
                  ORDER BY sr.version DESC, sr.id DESC
                ) rn
                FROM public.student_reports sr
                WHERE LOWER(TRIM(sr.teacher_last_name)) = LOWER(TRIM(?))
                  AND sr.upload_date >= ? AND sr.upload_date <= ?
              ) t WHERE rn = 1
            ),
            lv AS (
              SELECT * FROM (
                SELECT vr.*, ROW_NUMBER() OVER (PARTITION BY vr.report_id ORDER BY vr.checked_at DESC, vr.id DESC) rn
                FROM public.validation_results vr
              ) v WHERE rn = 1
            ),
            agg AS (
              SELECT tl.plan_row_id, tl.group_norm, tl.discipline_name,
                COALESCE(MAX(stud.students_cnt), 0)::int AS students_cnt,
                COALESCE(MAX(controls.controls_cnt), 1)::bigint AS controls_cnt,
                COUNT(lr.id)::bigint AS uploaded,
                ROUND(AVG(COALESCE(lv.percent, lr.check_percent)) FILTER (WHERE COALESCE(lv.percent, lr.check_percent) IS NOT NULL)::numeric, 0)::int AS avg_percent
              FROM tl
              LEFT JOIN stud ON stud.group_norm = tl.group_norm
              LEFT JOIN controls ON controls.plan_row_id = tl.plan_row_id
              LEFT JOIN lr ON lr.plan_row_id = tl.plan_row_id AND NULLIF(ltrim(btrim(lr.group_name), '0'), '') = tl.group_norm
              LEFT JOIN lv ON lv.report_id = lr.id
              GROUP BY tl.plan_row_id, tl.group_norm, tl.discipline_name
            )
            SELECT a.group_norm AS group_name, a.discipline_name,
              a.students_cnt AS students_cnt,
              a.uploaded AS uploaded,
              (a.students_cnt * a.controls_cnt)::bigint AS expected,
              a.avg_percent AS avg_percent
            FROM agg a
            ORDER BY a.discipline_name, a.group_norm
            """;
        try {
            return jdbc.query(sql, (rs, i) -> {
                GroupStatsRowDto dto = new GroupStatsRowDto();
                dto.setGroupName(rs.getString("group_name"));
                dto.setDisciplineName(rs.getString("discipline_name"));
                dto.setStudentsCount(rs.getInt("students_cnt"));
                dto.setUploaded(rs.getLong("uploaded"));
                dto.setExpected(rs.getLong("expected"));
                dto.setAvgPercent((Integer) rs.getObject("avg_percent"));
                return dto;
            }, ln, dept(), ln, df, dt);
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Мои дисциплины: агрегат по дисциплине — группы, студенты, загружено X/Y, прогресс, ср. %, критич. ошибки, предупреждения */
    public List<DisciplineTableRowDto> getTeacherDisciplineStats(String academicYear, String teacherLastName) {
        String ln = teacherLastName == null ? "" : teacherLastName.trim();

        String sql = """
            WITH tl AS (
              SELECT DISTINCT t.plan_row_id, t.discipline_name, NULLIF(ltrim(btrim(t.group_name), '0'), '') AS group_norm
              FROM public.teaching_load_csv t
              WHERE LOWER(TRIM(t.last_name)) = LOWER(TRIM(?)) AND t.department_name ILIKE ?
            ),
            stud AS (
              SELECT NULLIF(ltrim(btrim(s.group_name), '0'), '') AS group_norm, COUNT(*)::int AS students_cnt
              FROM public.students s
              WHERE s.status IS DISTINCT FROM 'Отчислен'
              GROUP BY 1
            ),
            controls AS (
              SELECT plan_row_id, GREATEST(COALESCE(COUNT(DISTINCT control_text), 0), 1)::bigint AS controls_cnt
              FROM public.control_schedule GROUP BY plan_row_id
            ),
            lr AS (
              SELECT * FROM (
                SELECT sr.*, ROW_NUMBER() OVER (
                  PARTITION BY sr.plan_row_id, sr.student_id, lower(btrim(sr.group_name)),
                    lower(btrim(sr.teacher_last_name)), lower(COALESCE(btrim(sr.control_type),'')),
                    COALESCE(btrim(sr.academic_year),'')
                  ORDER BY sr.version DESC, sr.id DESC
                ) rn
                FROM public.student_reports sr
                WHERE LOWER(TRIM(sr.teacher_last_name)) = LOWER(TRIM(?))
                  AND btrim(COALESCE(sr.academic_year, '')) = ?
              ) t WHERE rn = 1
            ),
            by_group AS (
              SELECT tl.plan_row_id, tl.group_norm, tl.discipline_name,
                COALESCE(MAX(stud.students_cnt), 0)::int AS students_cnt,
                COALESCE(MAX(controls.controls_cnt), 1)::bigint AS controls_cnt,
                COUNT(lr.id)::bigint AS uploaded
              FROM tl
              LEFT JOIN stud ON stud.group_norm = tl.group_norm
              LEFT JOIN controls ON controls.plan_row_id = tl.plan_row_id
              LEFT JOIN lr ON lr.plan_row_id = tl.plan_row_id AND NULLIF(ltrim(btrim(lr.group_name), '0'), '') = tl.group_norm
              GROUP BY tl.plan_row_id, tl.group_norm, tl.discipline_name
            )
            SELECT bg.discipline_name,
              COUNT(DISTINCT bg.group_norm)::int AS groups_cnt,
              SUM(bg.students_cnt)::int AS students_cnt,
              SUM(bg.uploaded)::bigint AS uploaded,
              SUM(bg.students_cnt * bg.controls_cnt)::bigint AS expected
            FROM by_group bg
            GROUP BY bg.discipline_name
            ORDER BY bg.discipline_name
            """;
        try {
            return jdbc.query(sql, (rs, i) -> {
                DisciplineTableRowDto dto = new DisciplineTableRowDto();
                dto.setDisciplineName(rs.getString("discipline_name"));
                dto.setGroupsCount(rs.getInt("groups_cnt"));
                dto.setStudentsCount(rs.getInt("students_cnt"));
                dto.setUploadedCount(rs.getLong("uploaded"));
                dto.setExpectedCount(rs.getLong("expected"));
                return dto;
            }, ln, dept(), ln, academicYear);
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Проблемные группы: низкий % и/или много невалидных. ТОП 10. */
    public List<ProblematicGroupDto> getProblematicGroups(LocalDate dateFrom, LocalDate dateTo, String teacherLastName) {
        Date df = dateFrom != null ? Date.valueOf(dateFrom) : Date.valueOf("1900-01-01");
        Date dt = dateTo != null ? Date.valueOf(dateTo) : Date.valueOf("2100-12-31");
        String teacherFilter = teacherLastName != null && !teacherLastName.isBlank()
            ? " AND LOWER(TRIM(sr.teacher_last_name)) = LOWER(TRIM(?))" : "";

        String sql = """
            WITH base AS (
              SELECT sr.id, NULLIF(ltrim(btrim(sr.group_name), '0'), '') AS group_name,
                COALESCE(lv.percent, sr.check_percent) AS percent, lv.valid
              FROM (
                SELECT sr2.*, ROW_NUMBER() OVER (
                  PARTITION BY sr2.plan_row_id, sr2.student_id, lower(btrim(sr2.group_name)),
                    lower(btrim(sr2.teacher_last_name)), lower(COALESCE(btrim(sr2.control_type),'')),
                    COALESCE(btrim(sr2.academic_year),'')
                  ORDER BY sr2.version DESC, sr2.id DESC
                ) rn
                FROM public.student_reports sr2
                WHERE sr2.upload_date >= ? AND sr2.upload_date <= ?
            """ + teacherFilter + """
              ) sr
              LEFT JOIN LATERAL (SELECT percent, valid FROM public.validation_results vr2
                WHERE vr2.report_id = sr.id ORDER BY vr2.checked_at DESC LIMIT 1) lv ON TRUE
              WHERE sr.rn = 1 AND sr.group_name IS NOT NULL
            )
            SELECT group_name AS group_name, COUNT(*)::bigint AS reports,
              ROUND(AVG(percent) FILTER (WHERE percent IS NOT NULL)::numeric, 0)::int AS avg_percent,
              COALESCE(SUM(CASE WHEN valid = false THEN 1 ELSE 0 END), 0)::bigint AS invalid_cnt
            FROM base
            GROUP BY group_name
            ORDER BY invalid_cnt DESC NULLS LAST, avg_percent ASC NULLS LAST
            LIMIT 10
            """;
        try {
            if (teacherLastName != null && !teacherLastName.isBlank()) {
                return jdbc.query(sql, (rs, i) -> {
                    ProblematicGroupDto dto = new ProblematicGroupDto();
                    dto.setGroupName(rs.getString("group_name"));
                    dto.setReports(rs.getLong("reports"));
                    dto.setAvgPercent((Integer) rs.getObject("avg_percent"));
                    dto.setInvalidCount(rs.getLong("invalid_cnt"));
                    return dto;
                }, df, dt, teacherLastName.trim());
            } else {
                return jdbc.query(sql.replace(teacherFilter, ""), (rs, i) -> {
                    ProblematicGroupDto dto = new ProblematicGroupDto();
                    dto.setGroupName(rs.getString("group_name"));
                    dto.setReports(rs.getLong("reports"));
                    dto.setAvgPercent((Integer) rs.getObject("avg_percent"));
                    dto.setInvalidCount(rs.getLong("invalid_cnt"));
                    return dto;
                }, df, dt);
            }
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Ошибки по дисциплинам и категориям (для heatmap) */
    public List<DisciplineErrorDto> getErrorsByDiscipline(LocalDate dateFrom, LocalDate dateTo, String teacherLastName) {
        Date df = dateFrom != null ? Date.valueOf(dateFrom) : Date.valueOf("1900-01-01");
        Date dt = dateTo != null ? Date.valueOf(dateTo) : Date.valueOf("2100-12-31");
        String teacherFilter = teacherLastName != null && !teacherLastName.isBlank()
            ? " AND LOWER(TRIM(sr.teacher_last_name)) = LOWER(TRIM(?))" : "";

        String sql = """
            WITH lv AS (
              SELECT vr.report_id, vr.errors_json,
                ROW_NUMBER() OVER (PARTITION BY vr.report_id ORDER BY vr.checked_at DESC, vr.id DESC) rn
              FROM public.validation_results vr
            )
            SELECT COALESCE(tl.discipline_name, '') AS discipline_name,
              COALESCE(e->>'title', e->>'code', 'Другое') AS category,
              COUNT(*)::bigint AS cnt
            FROM lv
            JOIN public.student_reports sr ON sr.id = lv.report_id
            LEFT JOIN (SELECT DISTINCT plan_row_id, discipline_name FROM public.teaching_load_csv) tl ON tl.plan_row_id = sr.plan_row_id
            CROSS JOIN LATERAL jsonb_array_elements(COALESCE(lv.errors_json, '[]'::jsonb)) e
            WHERE lv.rn = 1 AND lv.errors_json IS NOT NULL
              AND sr.upload_date >= ? AND sr.upload_date <= ?
            """ + teacherFilter + """
            GROUP BY tl.discipline_name, COALESCE(e->>'title', e->>'code', 'Другое')
            ORDER BY discipline_name, cnt DESC
            """;
        try {
            if (teacherLastName != null && !teacherLastName.isBlank()) {
                return jdbc.query(sql, (rs, i) -> new DisciplineErrorDto(
                    rs.getString("discipline_name"), rs.getString("category"), rs.getLong("cnt")),
                    df, dt, teacherLastName.trim());
            } else {
                return jdbc.query(sql.replace(teacherFilter, ""), (rs, i) -> new DisciplineErrorDto(
                    rs.getString("discipline_name"), rs.getString("category"), rs.getLong("cnt")), df, dt);
            }
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Критерии, которые чаще всего не проходят (из criteria_json) */
    public List<FailingCriterionDto> getFailingCriteria(LocalDate dateFrom, LocalDate dateTo, String teacherLastName) {
        Date df = dateFrom != null ? Date.valueOf(dateFrom) : Date.valueOf("1900-01-01");
        Date dt = dateTo != null ? Date.valueOf(dateTo) : Date.valueOf("2100-12-31");
        String teacherFilter = teacherLastName != null && !teacherLastName.isBlank()
            ? " AND LOWER(TRIM(sr.teacher_last_name)) = LOWER(TRIM(?))" : "";

        String sql = """
            WITH lv AS (
              SELECT vr.report_id, vr.criteria_json,
                ROW_NUMBER() OVER (PARTITION BY vr.report_id ORDER BY vr.checked_at DESC, vr.id DESC) rn
              FROM public.validation_results vr
            )
            SELECT t.k AS criterion,
              COUNT(*) FILTER (WHERE (t.v->>'passed')::boolean = false OR (t.v->>'valid')::boolean = false)::bigint AS failed_cnt,
              COUNT(*)::bigint AS total_cnt
            FROM lv
            JOIN public.student_reports sr ON sr.id = lv.report_id
            CROSS JOIN LATERAL jsonb_each(COALESCE(lv.criteria_json, '{}'::jsonb)) t(k, v)
            WHERE lv.rn = 1 AND lv.criteria_json IS NOT NULL AND jsonb_typeof(lv.criteria_json) = 'object'
              AND sr.upload_date >= ? AND sr.upload_date <= ?
            """ + teacherFilter + """
            GROUP BY t.k
            ORDER BY failed_cnt DESC
            LIMIT 10
            """;
        try {
            if (teacherLastName != null && !teacherLastName.isBlank()) {
                return jdbc.query(sql, (rs, i) -> {
                    FailingCriterionDto dto = new FailingCriterionDto();
                    dto.setCriterion(rs.getString("criterion"));
                    dto.setFailedCount(rs.getLong("failed_cnt"));
                    dto.setTotalCount(rs.getLong("total_cnt"));
                    return dto;
                }, df, dt, teacherLastName.trim());
            } else {
                return jdbc.query(sql.replace(teacherFilter, ""), (rs, i) -> {
                    FailingCriterionDto dto = new FailingCriterionDto();
                    dto.setCriterion(rs.getString("criterion"));
                    dto.setFailedCount(rs.getLong("failed_cnt"));
                    dto.setTotalCount(rs.getLong("total_cnt"));
                    return dto;
                }, df, dt);
            }
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Справочники для фильтров */
    public List<String> getDisciplines() {
        String sql = "SELECT DISTINCT t.discipline_name FROM public.teaching_load_csv t WHERE t.department_name ILIKE ? AND t.discipline_name IS NOT NULL AND TRIM(t.discipline_name) <> '' ORDER BY t.discipline_name";
        return jdbc.queryForList(sql, String.class, dept());
    }

    public List<String> getGroups() {
        String sql = "SELECT DISTINCT NULLIF(ltrim(btrim(g.name), '0'), '') AS group_norm FROM public.groups g WHERE g.name IS NOT NULL AND TRIM(g.name) <> '' ORDER BY group_norm";
        return jdbc.queryForList(sql, String.class);
    }

    public List<String> getTeachers() {
        String sql = "SELECT DISTINCT TRIM(t.last_name) FROM public.teaching_load_csv t WHERE t.department_name ILIKE ? AND t.last_name IS NOT NULL AND TRIM(t.last_name) <> '' ORDER BY TRIM(t.last_name)";
        return jdbc.queryForList(sql, String.class, dept());
    }

    public List<String> getWorkTypes() {
        String sql = "SELECT DISTINCT COALESCE(NULLIF(TRIM(work_type), ''), 'Не указан') FROM public.student_reports WHERE work_type IS NOT NULL ORDER BY 1";
        return jdbc.queryForList(sql, String.class);
    }

    public List<String> getStatuses() {
        String sql = "SELECT DISTINCT COALESCE(NULLIF(TRIM(status), ''), 'Не указан') FROM public.student_reports WHERE status IS NOT NULL ORDER BY 1";
        return jdbc.queryForList(sql, String.class);
    }

    public List<String> getControlTypes() {
        String sql = "SELECT DISTINCT COALESCE(NULLIF(TRIM(control_type), ''), '—') FROM public.student_reports WHERE control_type IS NOT NULL ORDER BY 1";
        return jdbc.queryForList(sql, String.class);
    }
}
