package com.etu.reports.repository;

import com.etu.reports.dto.report.MoodleLinkDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MoodleLinkRepository {

    private final JdbcTemplate jdbc;

    public MoodleLinkRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<MoodleLinkDto> findOne(int planRowId, String groupName, String controlType, String topic, String academicYear) {
        String sql = """
                SELECT plan_row_id, group_name, control_type, topic, academic_year, moodle_url,
                       updated_at::text AS updated_at, updated_by
                FROM public.moodle_links
                WHERE plan_row_id = ?
                  AND group_name = ?
                  AND control_type = ?
                  AND topic = ?
                  AND academic_year = ?
                """;
        List<MoodleLinkDto> list = jdbc.query(sql, (rs, i) -> {
            MoodleLinkDto dto = new MoodleLinkDto();
            dto.setPlanRowId(rs.getInt("plan_row_id"));
            dto.setGroupName(rs.getString("group_name"));
            dto.setControlType(rs.getString("control_type"));
            dto.setTopic(rs.getString("topic"));
            dto.setAcademicYear(rs.getString("academic_year"));
            dto.setMoodleUrl(rs.getString("moodle_url"));
            dto.setUpdatedAt(rs.getString("updated_at"));
            dto.setUpdatedBy(rs.getString("updated_by"));
            return dto;
        }, planRowId, groupName, controlType, topic, academicYear);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<MoodleLinkDto> findByPlanRowId(int planRowId) {
        String sql = """
                SELECT plan_row_id, group_name, control_type, topic, academic_year, moodle_url,
                       updated_at::text AS updated_at, updated_by
                FROM public.moodle_links
                WHERE plan_row_id = ?
                ORDER BY group_name, control_type, topic, academic_year
                """;
        return jdbc.query(sql, (rs, i) -> {
            MoodleLinkDto dto = new MoodleLinkDto();
            dto.setPlanRowId(rs.getInt("plan_row_id"));
            dto.setGroupName(rs.getString("group_name"));
            dto.setControlType(rs.getString("control_type"));
            dto.setTopic(rs.getString("topic"));
            dto.setAcademicYear(rs.getString("academic_year"));
            dto.setMoodleUrl(rs.getString("moodle_url"));
            dto.setUpdatedAt(rs.getString("updated_at"));
            dto.setUpdatedBy(rs.getString("updated_by"));
            return dto;
        }, planRowId);
    }

    public void upsert(int planRowId, String groupName, String controlType, String topic, String academicYear, String moodleUrl, String updatedBy) {
        String sql = """
                INSERT INTO public.moodle_links (
                    plan_row_id, group_name, control_type, topic, academic_year, moodle_url, updated_at, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?)
                ON CONFLICT (plan_row_id, group_name, control_type, topic, academic_year) DO UPDATE SET
                  moodle_url = EXCLUDED.moodle_url,
                  updated_at = CURRENT_TIMESTAMP,
                  updated_by = EXCLUDED.updated_by
                """;
        jdbc.update(sql, planRowId, groupName, controlType, topic, academicYear, moodleUrl, emptyToNull(updatedBy));
    }

    public int delete(int planRowId, String groupName, String controlType, String topic, String academicYear) {
        String sql = """
                DELETE FROM public.moodle_links
                WHERE plan_row_id = ?
                  AND group_name = ?
                  AND control_type = ?
                  AND topic = ?
                  AND academic_year = ?
                """;
        return jdbc.update(sql, planRowId, groupName, controlType, topic, academicYear);
    }

    private static String emptyToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
