package com.etu.reports.repository;

import com.etu.reports.dto.teacher.TeacherProfileDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public class TeacherProfileRepository {

    private static final Logger log = LoggerFactory.getLogger(TeacherProfileRepository.class);
    private static final String DEPT = "каф.ИС";

    private final JdbcTemplate jdbc;

    public TeacherProfileRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<TeacherProfileDto> MAPPER = (rs, i) -> {
        TeacherProfileDto dto = new TeacherProfileDto();
        dto.setLastName(rs.getString("last_name"));
        dto.setFirstName(rs.getString("first_name"));
        dto.setPatronymic(rs.getString("patronymic"));
        dto.setFio(rs.getString("fio"));
        dto.setEmail(rs.getString("email"));
        dto.setRole(rs.getString("role"));
        dto.setPosition(rs.getString("position"));
        dto.setRank(rs.getString("rank"));
        dto.setDegree(rs.getString("degree"));
        Object dc = rs.getObject("disciplines_count");
        dto.setDisciplinesCount(dc == null ? null : ((Number) dc).intValue());
        return dto;
    };

    public List<TeacherProfileDto> findAll(String search) {
        String s = search == null ? "" : search.trim();
        try {
            String sql = """
                WITH teacher_agg AS (
                  SELECT t.last_name,
                    -- В админке PK у teacher_profiles по last_name, поэтому агрегируем rank/degree тоже по фамилии,
                    -- чтобы не получать null из "чужой" группы (different first_name/patronymic при одинаковой фамилии).
                    MAX(t.first_name) AS first_name,
                    MAX(t.patronymic) AS patronymic,
                    -- В админке "position" выводится как "ранг + степень", чтобы не отображать должность.
                    CONCAT_WS(' ',
                      TRIM(NULLIF(STRING_AGG(DISTINCT t.rank, ', '), '')),
                      TRIM(NULLIF(STRING_AGG(DISTINCT t.degree, ', '), ''))
                    ) AS position,
                    TRIM(NULLIF(STRING_AGG(DISTINCT t.rank, ', '), '')) AS rank,
                    TRIM(NULLIF(STRING_AGG(DISTINCT t.degree, ', '), '')) AS degree,
                    COUNT(DISTINCT t.discipline_name) AS disc_cnt
                  FROM public.teaching_load_csv t
                  WHERE t.department_name ILIKE ? AND t.last_name IS NOT NULL AND TRIM(t.last_name) <> ''
                  GROUP BY t.last_name
                )
                SELECT ta.last_name, ta.first_name, ta.patronymic,
                  TRIM(COALESCE(ta.last_name,'') || ' ' || COALESCE(ta.first_name,'') || ' ' || COALESCE(ta.patronymic,'')) AS fio,
                  tp.email, COALESCE(tp."role", 'TEACHER') AS role,
                  ta.position, ta.rank, ta.degree, ta.disc_cnt AS disciplines_count
                FROM teacher_agg ta
                LEFT JOIN public.teacher_profiles tp ON LOWER(TRIM(tp.last_name)) = LOWER(TRIM(ta.last_name))
                WHERE (? IS NULL OR ? = '' OR ta.last_name ILIKE '%' || ? || '%' OR ta.first_name ILIKE '%' || ? || '%'
                  OR tp.email ILIKE '%' || ? || '%')
                ORDER BY ta.last_name, ta.first_name
                """;
            List<TeacherProfileDto> result = jdbc.query(sql, MAPPER, DEPT, s, s, s, s, s);
            log.info("TeacherProfileRepository.findAll(search='{}') -> {} rows", s, result.size());
            if (!result.isEmpty()) {
                TeacherProfileDto first = result.get(0);
                log.info(
                        "findAll first: lastName='{}', email='{}', role='{}', position='{}', rank='{}', degree='{}', disciplinesCount={}",
                        first.getLastName(), first.getEmail(), first.getRole(),
                        first.getPosition(), first.getRank(), first.getDegree(),
                        first.getDisciplinesCount()
                );
            }
            return result;
        } catch (Exception e) {
            log.warn("TeacherProfileRepository.findAll(search='{}') failed, fallback. Error: {}", s, e.getMessage());
            return findAllWithoutProfiles(s);
        }
    }

    private List<TeacherProfileDto> findAllWithoutProfiles(String search) {
        String sql = """
            SELECT t.last_name,
              MAX(t.first_name) AS first_name,
              MAX(t.patronymic) AS patronymic,
              TRIM(
                COALESCE(t.last_name,'') || ' ' ||
                COALESCE(MAX(t.first_name),'') || ' ' ||
                COALESCE(MAX(t.patronymic),'')
              ) AS fio,
              NULL AS email, 'TEACHER' AS role,
              -- В админке "position" выводится как "ранг + степень", чтобы не отображать должность.
              CONCAT_WS(' ',
                TRIM(NULLIF(STRING_AGG(DISTINCT t.rank, ', '), '')),
                TRIM(NULLIF(STRING_AGG(DISTINCT t.degree, ', '), ''))
              ) AS position,
              TRIM(NULLIF(STRING_AGG(DISTINCT t.rank, ', '), '')) AS rank,
              TRIM(NULLIF(STRING_AGG(DISTINCT t.degree, ', '), '')) AS degree,
              COUNT(DISTINCT t.discipline_name) AS disciplines_count
            FROM public.teaching_load_csv t
            WHERE t.department_name ILIKE ? AND t.last_name IS NOT NULL AND TRIM(t.last_name) <> ''
              AND (? IS NULL OR ? = '' OR t.last_name ILIKE '%' || ? || '%' OR t.first_name ILIKE '%' || ? || '%')
            GROUP BY t.last_name
            ORDER BY t.last_name, first_name
            """;
        return jdbc.query(sql, MAPPER, DEPT, search, search, search, search);
    }

    public Optional<TeacherProfileDto> findByLastName(String lastName) {
        String ln = lastName == null ? "" : lastName.trim();
        try {
            String sql = """
                WITH teacher_agg AS (
                  SELECT t.last_name,
                    MAX(t.first_name) AS first_name,
                    MAX(t.patronymic) AS patronymic,
                    -- В админке "position" выводится как "ранг + степень", чтобы не отображать должность.
                    CONCAT_WS(' ',
                      TRIM(NULLIF(STRING_AGG(DISTINCT t.rank, ', '), '')),
                      TRIM(NULLIF(STRING_AGG(DISTINCT t.degree, ', '), ''))
                    ) AS position,
                    TRIM(NULLIF(STRING_AGG(DISTINCT t.rank, ', '), '')) AS rank,
                    TRIM(NULLIF(STRING_AGG(DISTINCT t.degree, ', '), '')) AS degree,
                    COUNT(DISTINCT t.discipline_name) AS disc_cnt
                  FROM public.teaching_load_csv t
                  WHERE LOWER(TRIM(t.last_name)) = LOWER(TRIM(?)) AND t.department_name ILIKE ?
                  GROUP BY t.last_name
                )
                SELECT ta.last_name, ta.first_name, ta.patronymic,
                  TRIM(COALESCE(ta.last_name,'') || ' ' || COALESCE(ta.first_name,'') || ' ' || COALESCE(ta.patronymic,'')) AS fio,
                  tp.email, COALESCE(tp."role", 'TEACHER') AS role,
                  ta.position, ta.rank, ta.degree, ta.disc_cnt AS disciplines_count
                FROM teacher_agg ta
                LEFT JOIN public.teacher_profiles tp ON LOWER(TRIM(tp.last_name)) = LOWER(TRIM(ta.last_name))
                LIMIT 1
                """;
            List<TeacherProfileDto> list = jdbc.query(sql, MAPPER, ln, DEPT);
            return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
        } catch (Exception e) {
            return findByLastNameWithoutProfiles(ln);
        }
    }

    private Optional<TeacherProfileDto> findByLastNameWithoutProfiles(String lastName) {
        String sql = """
            SELECT t.last_name,
              MAX(t.first_name) AS first_name,
              MAX(t.patronymic) AS patronymic,
              TRIM(
                COALESCE(t.last_name,'') || ' ' ||
                COALESCE(MAX(t.first_name),'') || ' ' ||
                COALESCE(MAX(t.patronymic),'')
              ) AS fio,
              NULL AS email, 'TEACHER' AS role,
              -- В админке "position" выводится как "ранг + степень", чтобы не отображать должность.
              CONCAT_WS(' ',
                TRIM(NULLIF(STRING_AGG(DISTINCT t.rank, ', '), '')),
                TRIM(NULLIF(STRING_AGG(DISTINCT t.degree, ', '), ''))
              ) AS position,
              TRIM(NULLIF(STRING_AGG(DISTINCT t.rank, ', '), '')) AS rank,
              TRIM(NULLIF(STRING_AGG(DISTINCT t.degree, ', '), '')) AS degree,
              COUNT(DISTINCT t.discipline_name) AS disciplines_count
            FROM public.teaching_load_csv t
            WHERE LOWER(TRIM(t.last_name)) = LOWER(TRIM(?)) AND t.department_name ILIKE ?
            GROUP BY t.last_name
            LIMIT 1
            """;
        List<TeacherProfileDto> list = jdbc.query(sql, MAPPER, lastName, DEPT);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public void upsertRoleAndEmail(String lastName, String email, String role) {
        String ln = lastName == null ? "" : lastName.trim();
        String r = normalizeRoles(role);

        try {
            int updated = runUpdateRole(ln, r);
            if (updated == 0) {
                runInsertProfile(ln, r, email);
            }
            if (email != null && !email.isBlank()) {
                try {
                    jdbc.update("UPDATE public.teacher_profiles SET email = ? WHERE last_name = ?", email, ln);
                } catch (Exception ignored) {
                    // колонка email может отсутствовать
                }
            }
        } catch (Exception e) {
            ensureTeacherProfilesTable();
            int updated = runUpdateRole(ln, r);
            if (updated == 0) {
                runInsertProfile(ln, r, email);
            }
            if (email != null && !email.isBlank()) {
                try {
                    jdbc.update("UPDATE public.teacher_profiles SET email = ? WHERE last_name = ?", email, ln);
                } catch (Exception ignored) { }
            }
        }
    }

    private void ensureTeacherProfilesTable() {
        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS public.teacher_profiles (
                  last_name VARCHAR(255) NOT NULL PRIMARY KEY,
                  email VARCHAR(255),
                  "role" VARCHAR(50) NOT NULL DEFAULT 'TEACHER',
                  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
            log.info("Таблица teacher_profiles создана или уже существует");
        } catch (Exception e) {
            log.warn("Не удалось создать teacher_profiles: {}", e.getMessage());
        }
    }

    private int runUpdateRole(String ln, String r) {
        try {
            return jdbc.update("UPDATE public.teacher_profiles SET \"role\" = ? WHERE last_name = ?", r, ln);
        } catch (Exception e) {
            try {
                return jdbc.update("UPDATE teacher_profiles SET \"role\" = ? WHERE last_name = ?", r, ln);
            } catch (Exception e2) {
                throw new RuntimeException("Ошибка обновления teacher_profiles. Проверьте: 1) таблица teacher_profiles в БД; 2) колонки last_name, role. Детали: " + e.getMessage(), e);
            }
        }
    }

    private void runInsertProfile(String ln, String r, String email) {
        String insertSql = "INSERT INTO public.teacher_profiles (last_name, first_name, \"role\", created_at, updated_at) VALUES (?, NULL, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
        try {
            jdbc.update(insertSql, ln, r);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("column") && msg.contains("email")) {
                throw e;
            }
            try {
                jdbc.update("INSERT INTO teacher_profiles (last_name, first_name, \"role\", created_at, updated_at) VALUES (?, NULL, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", ln, r);
            } catch (Exception e2) {
                jdbc.update("""
                        INSERT INTO public.teacher_profiles (last_name, email, "role", updated_at)
                        VALUES (?, NULLIF(?, ''), ?, CURRENT_TIMESTAMP)
                        ON CONFLICT (last_name) DO UPDATE SET
                          email = COALESCE(NULLIF(?, ''), teacher_profiles.email),
                          "role" = ?,
                          updated_at = CURRENT_TIMESTAMP
                        """, ln, email, r, email, r);
            }
        }
    }

    public String getRole(String lastName) {
        try {
            String sql = "SELECT \"role\" FROM public.teacher_profiles WHERE last_name = ?";
            List<String> list = jdbc.query(sql, (rs, i) -> rs.getString("role"), lastName == null ? "" : lastName.trim());
            return list.isEmpty() ? "TEACHER" : list.get(0);
        } catch (Exception e) {
            return "TEACHER";
        }
    }

    private static String normalizeRoles(String raw) {
        if (raw == null || raw.isBlank()) return "TEACHER";
        Set<String> roles = new LinkedHashSet<>();
        for (String token : raw.split(",")) {
            String t = token == null ? "" : token.trim();
            if (t.isEmpty()) continue;
            String u = t.toUpperCase();
            if ("ADMINISTRATOR".equals(u)) u = "ADMIN";
            if ("HEAD".equals(u) || "HEAD OF DEPARTMENT".equals(u)) u = "HEAD_DEPARTMENT";
            if ("TEACHER".equals(u) || "ADMIN".equals(u) || "HEAD_DEPARTMENT".equals(u)) {
                roles.add(u);
            }
        }
        if (roles.isEmpty()) return "TEACHER";
        StringBuilder out = new StringBuilder();
        if (roles.contains("ADMIN")) out.append("ADMIN");
        if (roles.contains("HEAD_DEPARTMENT")) {
            if (!out.isEmpty()) out.append(",");
            out.append("HEAD_DEPARTMENT");
        }
        if (roles.contains("TEACHER")) {
            if (!out.isEmpty()) out.append(",");
            out.append("TEACHER");
        }
        return out.toString();
    }
}
