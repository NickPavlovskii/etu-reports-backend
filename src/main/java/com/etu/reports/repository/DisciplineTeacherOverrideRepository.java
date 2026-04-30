package com.etu.reports.repository;

import com.etu.reports.dto.admin.DisciplineTeacherAssignmentDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Фактический преподаватель по строке рабочей программы ({@code plan_row_id}), отдельно от {@code teaching_load_csv}.
 */
@Repository
public class DisciplineTeacherOverrideRepository {

    private static final String DEPT_FALLBACK = "каф.ИС";

    private final JdbcTemplate jdbc;

    @Value("${app.department-filter:каф.ИС}")
    private String departmentFilter;

    public DisciplineTeacherOverrideRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private String dept() {
        return departmentFilter != null && !departmentFilter.isBlank() ? departmentFilter : DEPT_FALLBACK;
    }

    private static final RowMapper<DisciplineTeacherAssignmentDto> ROW_MAPPER = (rs, i) -> {
        DisciplineTeacherAssignmentDto dto = new DisciplineTeacherAssignmentDto();
        dto.setPlanRowId(rs.getInt("plan_row_id"));
        dto.setDisciplineName(rs.getString("discipline_name"));
        int c = rs.getInt("course");
        dto.setCourse(rs.wasNull() ? null : c);
        int s = rs.getInt("semester");
        dto.setSemester(rs.wasNull() ? null : s);
        dto.setPlanLastName(rs.getString("plan_last_name"));
        dto.setPlanFirstName(rs.getString("plan_first_name"));
        dto.setPlanPatronymic(rs.getString("plan_patronymic"));
        dto.setActualLastName(rs.getString("actual_last_name"));
        dto.setActualFirstName(rs.getString("actual_first_name"));
        dto.setActualPatronymic(rs.getString("actual_patronymic"));
        dto.setHasOverride(rs.getBoolean("has_override"));
        return dto;
    };

    public List<DisciplineTeacherAssignmentDto> findAllAssignments(String search) {
        String q = search == null ? "" : search.trim();
        String sql = """
                SELECT x.plan_row_id, x.discipline_name, x.course, x.semester,
                  x.plan_last_name, x.plan_first_name, x.plan_patronymic,
                  COALESCE(NULLIF(BTRIM(o.actual_last_name), ''), x.plan_last_name) AS actual_last_name,
                  COALESCE(NULLIF(BTRIM(o.actual_first_name), ''), x.plan_first_name) AS actual_first_name,
                  COALESCE(NULLIF(BTRIM(o.actual_patronymic), ''), x.plan_patronymic) AS actual_patronymic,
                  (o.plan_row_id IS NOT NULL) AS has_override
                FROM (
                  SELECT DISTINCT ON (t.plan_row_id)
                    t.plan_row_id,
                    t.discipline_name,
                    t.course,
                    t.semester,
                    BTRIM(t.last_name) AS plan_last_name,
                    NULLIF(BTRIM(t.first_name), '') AS plan_first_name,
                    NULLIF(BTRIM(t.patronymic), '') AS plan_patronymic
                  FROM public.teaching_load_csv t
                  WHERE t.department_name ILIKE ?
                    AND t.discipline_name IS NOT NULL AND BTRIM(t.discipline_name) <> ''
                  ORDER BY t.plan_row_id, t.last_name, t.first_name
                ) x
                LEFT JOIN public.discipline_teacher_override o ON o.plan_row_id = x.plan_row_id
                WHERE (? = '' OR x.discipline_name ILIKE '%' || ? || '%'
                  OR x.plan_last_name ILIKE '%' || ? || '%'
                  OR x.plan_first_name ILIKE '%' || ? || '%'
                  OR COALESCE(BTRIM(o.actual_last_name), x.plan_last_name) ILIKE '%' || ? || '%')
                ORDER BY x.discipline_name, x.course NULLS LAST, x.semester NULLS LAST, x.plan_row_id
                """;
        return jdbc.query(sql, ROW_MAPPER, dept(), q, q, q, q, q);
    }

    public Optional<DisciplineTeacherAssignmentDto> findByPlanRowId(int planRowId) {
        String sql = """
                SELECT x.plan_row_id, x.discipline_name, x.course, x.semester,
                  x.plan_last_name, x.plan_first_name, x.plan_patronymic,
                  COALESCE(NULLIF(BTRIM(o.actual_last_name), ''), x.plan_last_name) AS actual_last_name,
                  COALESCE(NULLIF(BTRIM(o.actual_first_name), ''), x.plan_first_name) AS actual_first_name,
                  COALESCE(NULLIF(BTRIM(o.actual_patronymic), ''), x.plan_patronymic) AS actual_patronymic,
                  (o.plan_row_id IS NOT NULL) AS has_override
                FROM (
                  SELECT DISTINCT ON (t.plan_row_id)
                    t.plan_row_id,
                    t.discipline_name,
                    t.course,
                    t.semester,
                    BTRIM(t.last_name) AS plan_last_name,
                    NULLIF(BTRIM(t.first_name), '') AS plan_first_name,
                    NULLIF(BTRIM(t.patronymic), '') AS plan_patronymic
                  FROM public.teaching_load_csv t
                  WHERE t.plan_row_id = ? AND t.department_name ILIKE ?
                  ORDER BY t.plan_row_id, t.last_name, t.first_name
                ) x
                LEFT JOIN public.discipline_teacher_override o ON o.plan_row_id = x.plan_row_id
                """;
        List<DisciplineTeacherAssignmentDto> list = jdbc.query(sql, ROW_MAPPER, planRowId, dept());
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public boolean planRowExistsInLoad(int planRowId) {
        Integer n = jdbc.queryForObject(
                """
                SELECT COUNT(*)::int FROM public.teaching_load_csv t
                WHERE t.plan_row_id = ? AND t.department_name ILIKE ?
                """,
                Integer.class,
                planRowId,
                dept());
        return n != null && n > 0;
    }

    public void upsert(int planRowId, String actualLastName, String actualFirstName, String actualPatronymic, String updatedBy) {
        jdbc.update(
                """
                INSERT INTO public.discipline_teacher_override (
                    plan_row_id, actual_last_name, actual_first_name, actual_patronymic, updated_at, updated_by
                ) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, ?)
                ON CONFLICT (plan_row_id) DO UPDATE SET
                  actual_last_name = EXCLUDED.actual_last_name,
                  actual_first_name = EXCLUDED.actual_first_name,
                  actual_patronymic = EXCLUDED.actual_patronymic,
                  updated_at = CURRENT_TIMESTAMP,
                  updated_by = EXCLUDED.updated_by
                """,
                planRowId,
                actualLastName,
                emptyToNull(actualFirstName),
                emptyToNull(actualPatronymic),
                emptyToNull(updatedBy));
    }

    public int deleteByPlanRowId(int planRowId) {
        return jdbc.update("DELETE FROM public.discipline_teacher_override WHERE plan_row_id = ?", planRowId);
    }

    private static String emptyToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
