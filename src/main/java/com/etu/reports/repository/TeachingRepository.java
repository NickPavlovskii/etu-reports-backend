package com.etu.reports.repository;

import com.etu.reports.domain.Teaching;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class TeachingRepository {

    private static final String DEPT_VALUE = "каф.ИС";

    private final JdbcTemplate jdbc;

    public TeachingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<Teaching> TEACHING_MAPPER = (rs, rowNum) -> {
        Teaching t = new Teaching();
        t.setDisciplineName(rs.getString("discipline_name"));
        t.setGroupName(rs.getString("group_name"));
        t.setLastName(rs.getString("last_name"));
        t.setFirstName(rs.getString("first_name"));
        t.setPatronymic(rs.getString("patronymic"));
        t.setDepartmentName(rs.getString("department_name"));
        t.setPosition(rs.getString("position"));
        t.setRank(rs.getString("rank"));
        t.setDegree(rs.getString("degree"));
        return t;
    };

    public List<String> findDistinctDisciplinesByLastName(String lastName) {
        String ln = lastName == null ? "" : lastName.trim();
        String sql = "SELECT DISTINCT t.discipline_name " +
                "FROM public.teaching_load_csv t " +
                "WHERE LOWER(TRIM(t.last_name)) = LOWER(TRIM(?)) " +
                "  AND t.first_name IS NOT NULL AND t.first_name <> '' " +
                "  AND t.discipline_name IS NOT NULL AND t.discipline_name <> '' " +
                "  AND t.department_name ILIKE ? " +
                "ORDER BY t.discipline_name";
        return jdbc.queryForList(sql, String.class, ln, DEPT_VALUE);
    }

    public List<Teaching> findByLastName(String lastName) {
        String ln = lastName == null ? "" : lastName.trim();
        String sql = "SELECT DISTINCT " +
                "  t.discipline_name, t.group_name, " +
                "  t.last_name, t.first_name, t.patronymic, " +
                "  t.department_name, t.position, t.rank, t.degree " +
                "FROM public.teaching_load_csv t " +
                "WHERE LOWER(TRIM(t.last_name)) = LOWER(TRIM(?)) " +
                "  AND t.first_name IS NOT NULL AND t.first_name <> '' " +
                "  AND t.discipline_name IS NOT NULL AND t.discipline_name <> '' " +
                "  AND t.department_name ILIKE ? " +
                "ORDER BY t.discipline_name, t.group_name";
        return jdbc.query(sql, TEACHING_MAPPER, ln, DEPT_VALUE);
    }

    public Teaching findTeacherProfileByLastName(String lastName) {
        String ln = lastName == null ? "" : lastName.trim();
        String sql = "SELECT " +
                "  t.last_name, t.first_name, t.patronymic, " +
                "  t.department_name AS department_name, " +
                "  t.position       AS position, " +
                "  t.rank           AS rank, " +
                "  t.degree         AS degree " +
                "FROM public.teaching_load_csv t " +
                "WHERE LOWER(TRIM(t.last_name)) = LOWER(TRIM(?)) " +
                "  AND t.first_name IS NOT NULL AND t.first_name <> '' " +
                "  AND t.department_name ILIKE ? " +
                "ORDER BY (t.rank IS NULL OR t.rank = '') ASC, (t.degree IS NULL OR t.degree = '') ASC " +
                "LIMIT 1";
        List<Teaching> list = jdbc.query(sql, (rs, rowNum) -> {
            Teaching t = new Teaching();
            t.setLastName(rs.getString("last_name"));
            t.setFirstName(rs.getString("first_name"));
            t.setPatronymic(rs.getString("patronymic"));
            t.setDepartmentName(rs.getString("department_name"));
            t.setPosition(rs.getString("position"));
            t.setRank(rs.getString("rank"));
            t.setDegree(rs.getString("degree"));
            return t;
        }, ln, DEPT_VALUE);
        return list.isEmpty() ? null : list.get(0);
    }
}
