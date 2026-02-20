package com.etu.reports.repository;

import com.etu.reports.dto.ControlScheduleDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Repository
public class ControlScheduleRepository {

    private final JdbcTemplate jdbc;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public ControlScheduleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ControlScheduleDto> findControlsByTeacherAndPlanRowId(String lastName, int planRowId) {
        String sql = "WITH groups AS (" +
                "  SELECT DISTINCT trim(t.group_name) AS group_name " +
                "  FROM public.teaching_load_csv t " +
                "  WHERE t.plan_row_id = ? AND t.last_name ILIKE ? AND trim(t.group_name) <> ''" +
                ") " +
                "SELECT g.group_name, cs.week_start, cs.week_end, cs.control_text, cs.topics " +
                "FROM groups g JOIN public.control_schedule cs ON cs.plan_row_id = ? " +
                "ORDER BY g.group_name, cs.week_start, cs.week_end, cs.control_text";

        String pattern = "%" + (lastName == null ? "" : lastName.trim()) + "%";
        return jdbc.query(sql, (rs, rowNum) -> {
            String groupName = rs.getString("group_name");
            int weekStart = rs.getInt("week_start");
            int weekEnd = rs.getInt("week_end");
            String controlText = rs.getString("control_text");
            String topicsText = rs.getString("topics");
            List<String> topics = parseTopicsJson(topicsText);
            return new ControlScheduleDto(groupName, weekStart, weekEnd, controlText, topics);
        }, planRowId, pattern, planRowId);
    }

    private static List<String> parseTopicsJson(String s) {
        if (s == null || s.isBlank()) return Collections.emptyList();
        String t = s.trim();
        if (t.startsWith("[")) {
            try {
                return objectMapper.readValue(t, new TypeReference<List<String>>() {});
            } catch (Exception e) {
                return fallbackParse(t);
            }
        }
        return List.of(t);
    }

    private static List<String> fallbackParse(String t) {
        List<String> out = new ArrayList<>();
        t = t.replaceAll("^\\[|\\]$", "").trim();
        if (t.isEmpty()) return out;
        for (String part : t.split("\",\\s*\"")) {
            String x = part.replaceAll("^\"|\"$", "").trim();
            if (!x.isEmpty()) out.add(x);
        }
        return out.isEmpty() ? List.of(t) : out;
    }
}
