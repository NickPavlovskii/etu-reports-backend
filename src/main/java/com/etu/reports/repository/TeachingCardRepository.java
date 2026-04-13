package com.etu.reports.repository;

import com.etu.reports.domain.DisciplineCard;
import com.etu.reports.dto.student.StudentRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class TeachingCardRepository {

    private static final Logger log = LoggerFactory.getLogger(TeachingCardRepository.class);

    private static final String DEPT_VALUE = "каф.ИС";

    private final JdbcTemplate jdbc;

    public TeachingCardRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<DisciplineCard> CARD_MAPPER = (rs, rowNum) -> {
        DisciplineCard c = new DisciplineCard();
        c.setPlanRowId(rs.getInt("plan_row_id"));
        c.setDisciplineName(rs.getString("discipline_name"));
        c.setCourse(rs.getInt("course"));
        c.setSemester(rs.getInt("semester"));
        c.setGroupsCount(rs.getInt("groups_count"));
        c.setLectureHours(rs.getBigDecimal("lecture_hours"));
        c.setPracticeHours(rs.getBigDecimal("practice_hours"));
        c.setHasExam(rs.getBoolean("has_exam"));
        c.setHasPass(rs.getBoolean("has_pass"));
        c.setHasPassMark(rs.getBoolean("has_pass_mark"));
        c.setEducationForm(rs.getString("education_form"));
        c.setEducationLevel(rs.getString("education_level"));
        return c;
    };

    private static String lastNameForMatch(String lastName) {
        return (lastName == null ? "" : lastName.trim());
    }

    public List<String> findAllGroupsByLastName(String lastName) {
        String sql = "WITH t_norm AS (" +
                "  SELECT t.*, NULLIF(ltrim(btrim(t.group_name), '0'), '') AS group_norm " +
                "  FROM public.teaching_load_csv t " +
                "), s_norm AS (" +
                "  SELECT s.*, NULLIF(ltrim(btrim(s.group_name), '0'), '') AS group_norm " +
                "  FROM public.students s" +
                ") " +
                "SELECT DISTINCT t.group_norm AS group_name " +
                "FROM t_norm t " +
                "WHERE LOWER(TRIM(t.last_name)) = LOWER(TRIM(?)) AND t.department_name ILIKE ? " +
                "  AND t.group_norm IS NOT NULL " +
                "  AND EXISTS (SELECT 1 FROM s_norm s WHERE s.group_norm = t.group_norm AND s.status <> 'Отчислен') " +
                "ORDER BY group_name";
        return jdbc.queryForList(sql, String.class, lastNameForMatch(lastName), DEPT_VALUE);
    }

    public List<DisciplineCard> findDisciplineCardsByLastName(String lastName, String academicYear) {
        String ln = lastNameForMatch(lastName);
        log.debug("findDisciplineCardsByLastName: lastName='{}', dept='{}'", lastName, DEPT_VALUE);

        String sql = "WITH t_norm AS (" +
                "  SELECT t.*, NULLIF(ltrim(btrim(t.group_name), '0'), '') AS group_norm " +
                "  FROM public.teaching_load_csv t " +
                "), s_norm AS (" +
                "  SELECT s.*, NULLIF(ltrim(btrim(s.group_name), '0'), '') AS group_norm " +
                "  FROM public.students s" +
                ") " +
                "SELECT MIN(t.plan_row_id) AS plan_row_id, t.discipline_name, t.course, t.semester, " +
                "  COUNT(DISTINCT t.group_norm) FILTER (WHERE t.group_norm IS NOT NULL " +
                "    AND EXISTS (SELECT 1 FROM s_norm s WHERE s.group_norm = t.group_norm AND s.status <> 'Отчислен')) AS groups_count, " +
                "  COALESCE(SUM(CASE WHEN t.activity_type ILIKE 'Лек%' THEN t.hours ELSE 0 END), 0) AS lecture_hours, " +
                "  COALESCE(SUM(CASE WHEN t.activity_type ILIKE 'Пр%' THEN t.hours ELSE 0 END), 0) AS practice_hours, " +
                "  BOOL_OR(t.has_exam) AS has_exam, BOOL_OR(t.has_pass) AS has_pass, BOOL_OR(t.has_pass_mark) AS has_pass_mark, " +
                "  TRIM(BOTH ', ' FROM STRING_AGG(DISTINCT NULLIF(TRIM(g.education_form), ''), ', ')) AS education_form, " +
                "  TRIM(BOTH ', ' FROM STRING_AGG(DISTINCT NULLIF(TRIM(g.education_level), ''), ', ')) AS education_level " +
                "FROM t_norm t " +
                "LEFT JOIN public.groups g ON NULLIF(ltrim(btrim(g.name), '0'), '') = t.group_norm AND t.course = g.course AND g.academic_year = ? " +
                "WHERE LOWER(TRIM(t.last_name)) = LOWER(TRIM(?)) AND t.first_name IS NOT NULL AND t.first_name <> '' " +
                "  AND t.discipline_name IS NOT NULL AND t.discipline_name <> '' AND t.department_name ILIKE ? " +
                "GROUP BY t.discipline_name, t.course, t.semester " +
                "ORDER BY t.discipline_name, t.course, t.semester";
        List<DisciplineCard> result = jdbc.query(sql, CARD_MAPPER, academicYear, ln, DEPT_VALUE);
        log.debug("findDisciplineCardsByLastName: found {} cards", result.size());
        return result;
    }

    public DisciplineCard findDisciplineCardByPlanRowId(String lastName, int planRowId, String academicYear) {
        String sql = "WITH t_norm AS (" +
                "  SELECT t.*, NULLIF(ltrim(btrim(t.group_name), '0'), '') AS group_norm " +
                "  FROM public.teaching_load_csv t " +
                "), s_norm AS (" +
                "  SELECT s.*, NULLIF(ltrim(btrim(s.group_name), '0'), '') AS group_norm " +
                "  FROM public.students s" +
                ") " +
                "SELECT MIN(t.plan_row_id) AS plan_row_id, t.discipline_name, t.course, t.semester, " +
                "  COUNT(DISTINCT t.group_norm) FILTER (WHERE t.group_norm IS NOT NULL " +
                "    AND EXISTS (SELECT 1 FROM s_norm s WHERE s.group_norm = t.group_norm AND s.status <> 'Отчислен')) AS groups_count, " +
                "  COALESCE(SUM(CASE WHEN t.activity_type ILIKE 'Лек%' THEN t.hours ELSE 0 END), 0) AS lecture_hours, " +
                "  COALESCE(SUM(CASE WHEN t.activity_type ILIKE 'Пр%' THEN t.hours ELSE 0 END), 0) AS practice_hours, " +
                "  BOOL_OR(t.has_exam) AS has_exam, BOOL_OR(t.has_pass) AS has_pass, BOOL_OR(t.has_pass_mark) AS has_pass_mark, " +
                "  TRIM(BOTH ', ' FROM STRING_AGG(DISTINCT NULLIF(TRIM(g.education_form), ''), ', ')) AS education_form, " +
                "  TRIM(BOTH ', ' FROM STRING_AGG(DISTINCT NULLIF(TRIM(g.education_level), ''), ', ')) AS education_level " +
                "FROM t_norm t " +
                "LEFT JOIN public.groups g ON NULLIF(ltrim(btrim(g.name), '0'), '') = t.group_norm AND t.course = g.course AND g.academic_year = ? " +
                "WHERE t.plan_row_id = ? AND LOWER(TRIM(t.last_name)) = LOWER(TRIM(?)) AND t.department_name ILIKE ? " +
                "GROUP BY t.discipline_name, t.course, t.semester " +
                "LIMIT 1";
        List<DisciplineCard> list = jdbc.query(sql, CARD_MAPPER, academicYear, planRowId, lastNameForMatch(lastName), DEPT_VALUE);
        return list.isEmpty() ? null : list.get(0);
    }

    public List<String> findGroupsByPlanRowId(String lastName, int planRowId) {
        String sql = "WITH t_norm AS (" +
                "  SELECT t.*, NULLIF(ltrim(btrim(t.group_name), '0'), '') AS group_norm " +
                "  FROM public.teaching_load_csv t " +
                "), s_norm AS (" +
                "  SELECT s.*, NULLIF(ltrim(btrim(s.group_name), '0'), '') AS group_norm " +
                "  FROM public.students s" +
                ") " +
                "SELECT DISTINCT t.group_norm AS group_name " +
                "FROM t_norm t " +
                "WHERE t.plan_row_id = ? AND LOWER(TRIM(t.last_name)) = LOWER(TRIM(?)) AND t.department_name ILIKE ? " +
                "  AND t.group_norm IS NOT NULL " +
                "  AND EXISTS (SELECT 1 FROM s_norm s WHERE s.group_norm = t.group_norm AND s.status <> 'Отчислен') " +
                "ORDER BY group_name";
        return jdbc.queryForList(sql, String.class, planRowId, lastNameForMatch(lastName), DEPT_VALUE);
    }

    public List<StudentRow> findStudentsByPlanRowId(String lastName, int planRowId) {
        String sql = "WITH t_norm AS (" +
                "  SELECT t.*, NULLIF(ltrim(btrim(t.group_name), '0'), '') AS group_norm " +
                "  FROM public.teaching_load_csv t " +
                "), s_norm AS (" +
                "  SELECT s.*, NULLIF(ltrim(btrim(s.group_name), '0'), '') AS group_norm " +
                "  FROM public.students s" +
                ") " +
                "SELECT s.lk_id, s.group_name, s.record_book, " +
                "  NULLIF(TRIM(COALESCE(sf.last_name,'') || ' ' || COALESCE(sf.first_name,'') || ' ' || COALESCE(sf.patronymic,'')), '') AS fio " +
                "FROM s_norm s " +
                "LEFT JOIN public.student_fio sf ON sf.id = s.lk_id " +
                "WHERE EXISTS (SELECT 1 FROM t_norm t " +
                "  WHERE t.plan_row_id = ? AND LOWER(TRIM(t.last_name)) = LOWER(TRIM(?)) AND t.group_norm IS NOT NULL " +
                "  AND s.group_norm = t.group_norm) " +
                "ORDER BY s.group_name, s.lk_id";
        return jdbc.query(sql, (rs, rowNum) -> new StudentRow(
                rs.getInt("lk_id"),
                rs.getString("group_name"),
                rs.getString("record_book"),
                rs.getString("fio")
        ), planRowId, lastNameForMatch(lastName));
    }
}
