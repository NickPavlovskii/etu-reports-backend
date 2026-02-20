package com.etu.reports.repository;

import com.etu.reports.config.AppProperties;
import com.etu.reports.domain.Teaching;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class TeachingRepository {

    private final JdbcTemplate jdbc;
    private final AppProperties appProperties;

    public TeachingRepository(JdbcTemplate jdbc, AppProperties appProperties) {
        this.jdbc = jdbc;
        this.appProperties = appProperties;
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
        String sql = "SELECT DISTINCT t.discipline_name " +
                "FROM teaching_load_csv t " +
                "WHERE t.last_name ILIKE ? " +
                "  AND t.first_name IS NOT NULL AND t.first_name <> '' " +
                "  AND t.discipline_name IS NOT NULL AND t.discipline_name <> '' " +
                "  AND t.department_name = ? " +
                "ORDER BY t.discipline_name";
        return jdbc.queryForList(sql, String.class, lastName, appProperties.getDepartmentFilter());
    }

    public List<Teaching> findByLastName(String lastName) {
        String sql = "SELECT DISTINCT " +
                "  t.discipline_name, t.group_name, " +
                "  t.last_name, t.first_name, t.patronymic, " +
                "  t.department_name, t.position, t.rank, t.degree " +
                "FROM teaching_load_csv t " +
                "WHERE t.last_name ILIKE ? " +
                "  AND t.first_name IS NOT NULL AND t.first_name <> '' " +
                "  AND t.discipline_name IS NOT NULL AND t.discipline_name <> '' " +
                "  AND t.department_name = ? " +
                "ORDER BY t.discipline_name, t.group_name";
        return jdbc.query(sql, TEACHING_MAPPER, lastName, appProperties.getDepartmentFilter());
    }

    public Teaching findTeacherProfileByLastName(String lastName) {
        String sql = "SELECT " +
                "  t.last_name, t.first_name, t.patronymic, " +
                "  t.department_name AS department_name, " +
                "  t.position       AS position, " +
                "  t.rank           AS rank, " +
                "  t.degree         AS degree " +
                "FROM teaching_load_csv t " +
                "WHERE t.last_name ILIKE ? " +
                "  AND t.first_name IS NOT NULL AND t.first_name <> '' " +
                "  AND t.department_name = ? " +
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
        }, lastName, appProperties.getDepartmentFilter());
        return list.isEmpty() ? null : list.get(0);
    }
}
