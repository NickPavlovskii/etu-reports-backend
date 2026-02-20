package com.etu.reports.repository;

import com.etu.reports.config.AppProperties;
import com.etu.reports.domain.DisciplineCard;
import com.etu.reports.dto.StudentRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class TeachingCardRepository {

    private final JdbcTemplate jdbc;
    private final AppProperties appProperties;

    public TeachingCardRepository(JdbcTemplate jdbc, AppProperties appProperties) {
        this.jdbc = jdbc;
        this.appProperties = appProperties;
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
        return c;
    };

    private static String lastNamePattern(String lastName) {
        return "%" + (lastName == null ? "" : lastName.trim()) + "%";
    }

    public List<String> findAllGroupsByLastName(String lastName) {
        String sql = "SELECT DISTINCT btrim(t.group_name) AS group_name " +
                "FROM teaching_load_csv t " +
                "WHERE t.last_name ILIKE ? AND t.department_name = ? " +
                "  AND t.group_name IS NOT NULL AND btrim(t.group_name) <> '' " +
                "  AND EXISTS (SELECT 1 FROM students s WHERE btrim(s.group_name) = btrim(t.group_name) AND s.status <> 'Отчислен') " +
                "ORDER BY group_name";
        return jdbc.queryForList(sql, String.class, lastNamePattern(lastName), appProperties.getDepartmentFilter());
    }

    public List<DisciplineCard> findDisciplineCardsByLastName(String lastName) {
        String sql = "SELECT MIN(t.plan_row_id) AS plan_row_id, t.discipline_name, t.course, t.semester, " +
                "  COUNT(DISTINCT btrim(t.group_name)) FILTER (WHERE t.group_name IS NOT NULL AND btrim(t.group_name) <> '' " +
                "    AND EXISTS (SELECT 1 FROM students s WHERE btrim(s.group_name) = btrim(t.group_name) AND s.status <> 'Отчислен')) AS groups_count, " +
                "  COALESCE(SUM(CASE WHEN t.activity_type ILIKE 'Лек%' THEN t.hours ELSE 0 END), 0) AS lecture_hours, " +
                "  COALESCE(SUM(CASE WHEN t.activity_type ILIKE 'Пр%' THEN t.hours ELSE 0 END), 0) AS practice_hours, " +
                "  BOOL_OR(t.has_exam) AS has_exam, BOOL_OR(t.has_pass) AS has_pass, BOOL_OR(t.has_pass_mark) AS has_pass_mark " +
                "FROM teaching_load_csv t " +
                "WHERE t.last_name ILIKE ? AND t.first_name IS NOT NULL AND t.first_name <> '' " +
                "  AND t.discipline_name IS NOT NULL AND t.discipline_name <> '' AND t.department_name = ? " +
                "GROUP BY t.discipline_name, t.course, t.semester " +
                "ORDER BY t.discipline_name, t.course, t.semester";
        return jdbc.query(sql, CARD_MAPPER, lastNamePattern(lastName), appProperties.getDepartmentFilter());
    }

    public DisciplineCard findDisciplineCardByPlanRowId(String lastName, int planRowId) {
        String sql = "SELECT MIN(t.plan_row_id) AS plan_row_id, t.discipline_name, t.course, t.semester, " +
                "  COUNT(DISTINCT btrim(t.group_name)) FILTER (WHERE t.group_name IS NOT NULL AND btrim(t.group_name) <> '' " +
                "    AND EXISTS (SELECT 1 FROM students s WHERE btrim(s.group_name) = btrim(t.group_name) AND s.status <> 'Отчислен')) AS groups_count, " +
                "  COALESCE(SUM(CASE WHEN t.activity_type ILIKE 'Лек%' THEN t.hours ELSE 0 END), 0) AS lecture_hours, " +
                "  COALESCE(SUM(CASE WHEN t.activity_type ILIKE 'Пр%' THEN t.hours ELSE 0 END), 0) AS practice_hours, " +
                "  BOOL_OR(t.has_exam) AS has_exam, BOOL_OR(t.has_pass) AS has_pass, BOOL_OR(t.has_pass_mark) AS has_pass_mark " +
                "FROM teaching_load_csv t " +
                "WHERE t.plan_row_id = ? AND t.last_name ILIKE ? AND t.department_name = ? " +
                "GROUP BY t.discipline_name, t.course, t.semester " +
                "LIMIT 1";
        List<DisciplineCard> list = jdbc.query(sql, CARD_MAPPER, planRowId, lastNamePattern(lastName), appProperties.getDepartmentFilter());
        return list.isEmpty() ? null : list.get(0);
    }

    public List<String> findGroupsByPlanRowId(String lastName, int planRowId) {
        String sql = "SELECT DISTINCT btrim(t.group_name) AS group_name " +
                "FROM teaching_load_csv t " +
                "WHERE t.plan_row_id = ? AND t.last_name ILIKE ? AND t.department_name = ? " +
                "  AND t.group_name IS NOT NULL AND btrim(t.group_name) <> '' " +
                "  AND EXISTS (SELECT 1 FROM students s WHERE btrim(s.group_name) = btrim(t.group_name) AND s.status <> 'Отчислен') " +
                "ORDER BY group_name";
        return jdbc.queryForList(sql, String.class, planRowId, lastNamePattern(lastName), appProperties.getDepartmentFilter());
    }

    public List<StudentRow> findStudentsByPlanRowId(String lastName, int planRowId) {
        String sql = "SELECT s.lk_id, s.group_name, s.record_book " +
                "FROM students s " +
                "WHERE EXISTS (SELECT 1 FROM teaching_load_csv t " +
                "  WHERE t.plan_row_id = ? AND t.last_name ILIKE ? AND btrim(t.group_name) <> '' " +
                "  AND btrim(s.group_name) = btrim(t.group_name)) " +
                "ORDER BY s.group_name, s.lk_id";
        return jdbc.query(sql, (rs, rowNum) -> new StudentRow(
                rs.getInt("lk_id"),
                rs.getString("group_name"),
                rs.getString("record_book")
        ), planRowId, lastNamePattern(lastName));
    }
}
