package com.etu.reports.repository;

import com.etu.reports.dto.admin.AdminStatsDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class AdminStatsRepository {

    private static final String DEPT_VALUE = "каф.ИС";

    private final JdbcTemplate jdbc;

    public AdminStatsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public AdminStatsDto getStats(String academicYear) {
        String sql = """
            SELECT
              (SELECT COUNT(*) FROM (
                SELECT 1 FROM public.teaching_load_csv t
                WHERE t.department_name ILIKE ? AND t.last_name IS NOT NULL AND TRIM(t.last_name) <> ''
                  AND t.first_name IS NOT NULL AND TRIM(t.first_name) <> ''
                GROUP BY LOWER(TRIM(t.last_name))
              ) x) AS teachers,
              (SELECT COUNT(DISTINCT t.discipline_name) FROM public.teaching_load_csv t
               WHERE t.department_name ILIKE ? AND t.discipline_name IS NOT NULL AND TRIM(t.discipline_name) <> '') AS disciplines,
              (SELECT COUNT(DISTINCT NULLIF(ltrim(btrim(t.group_name), '0'), '')) FROM public.teaching_load_csv t
               WHERE t.department_name ILIKE ? AND t.group_name IS NOT NULL AND TRIM(t.group_name) <> '') AS groups_load,
              (SELECT COUNT(DISTINCT plan_row_id) FROM public.control_schedule) AS templates
            """;
        try {
            Integer teachers = null, disciplines = null, groupsLoad = null, templates = null;
            List<Map<String, Object>> rows = jdbc.queryForList(sql, DEPT_VALUE, DEPT_VALUE, DEPT_VALUE);
            if (rows != null && !rows.isEmpty()) {
                Map<String, Object> row = rows.get(0);
                teachers = row.get("teachers") != null ? ((Number) row.get("teachers")).intValue() : 0;
                disciplines = row.get("disciplines") != null ? ((Number) row.get("disciplines")).intValue() : 0;
                groupsLoad = row.get("groups_load") != null ? ((Number) row.get("groups_load")).intValue() : 0;
                templates = row.get("templates") != null ? ((Number) row.get("templates")).intValue() : 0;
            }
            int groups = (academicYear != null && !academicYear.isBlank())
                    ? countGroupsByAcademicYear(academicYear.trim())
                    : (groupsLoad != null ? groupsLoad : 0);
            return AdminStatsDto.of(
                    teachers != null ? teachers : 0,
                    disciplines != null ? disciplines : 0,
                    groups,
                    templates != null ? templates : 0
            );
        } catch (Exception e) {
            return AdminStatsDto.of(0, 0, 0, 0);
        }
    }

    private int countGroupsByAcademicYear(String academicYear) {
        try {
            String sql = "SELECT COUNT(*) FROM (SELECT 1 FROM public.groups g WHERE g.academic_year = ? GROUP BY g.name, g.course) x";
            Integer n = jdbc.queryForObject(sql, Integer.class, academicYear);
            return n != null ? n : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
