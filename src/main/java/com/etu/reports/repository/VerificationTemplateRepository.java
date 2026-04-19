package com.etu.reports.repository;

import com.etu.reports.dto.template.TemplateCriteriaDto;
import com.etu.reports.dto.template.VerificationTemplateDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class VerificationTemplateRepository {

    private static final Logger log = LoggerFactory.getLogger(VerificationTemplateRepository.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public VerificationTemplateRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<VerificationTemplateDto> findAll() {
        String sql = "SELECT id, name, description, criteria, created_at, updated_at " +
                "FROM public.verification_templates ORDER BY name";
        return jdbc.query(sql, this::mapRow);
    }

    public Optional<VerificationTemplateDto> findById(int id) {
        String sql = "SELECT id, name, description, criteria, created_at, updated_at " +
                "FROM public.verification_templates WHERE id = ?";
        List<VerificationTemplateDto> list = jdbc.query(sql, this::mapRow, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public VerificationTemplateDto create(String name, String description, TemplateCriteriaDto criteria) {
        String json = toJson(criteria);
        String sql = "INSERT INTO public.verification_templates (name, description, criteria) " +
                "VALUES (?, ?, ?::jsonb) RETURNING id, name, description, criteria, created_at, updated_at";
        return jdbc.queryForObject(sql, this::mapRow, name, description, json);
    }

    public Optional<VerificationTemplateDto> update(int id, String name, String description, TemplateCriteriaDto criteria) {
        String json = toJson(criteria);
        String sql = "UPDATE public.verification_templates SET name = ?, description = ?, criteria = ?::jsonb, " +
                "updated_at = CURRENT_TIMESTAMP WHERE id = ? RETURNING id, name, description, criteria, created_at, updated_at";
        List<VerificationTemplateDto> list = jdbc.query(sql, this::mapRow, name, description, json, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public boolean deleteById(int id) {
        int rows = jdbc.update("DELETE FROM public.verification_templates WHERE id = ?", id);
        return rows > 0;
    }

    private VerificationTemplateDto mapRow(ResultSet rs, int rowNum) throws java.sql.SQLException {
        TemplateCriteriaDto criteria = fromJson(rs.getString("criteria"));
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp updated = rs.getTimestamp("updated_at");
        return VerificationTemplateDto.builder()
                .id(rs.getInt("id"))
                .name(rs.getString("name"))
                .description(rs.getString("description"))
                .criteria(criteria)
                .createdAt(created == null ? null : created.toInstant())
                .updatedAt(updated == null ? null : updated.toInstant())
                .build();
    }

    private String toJson(TemplateCriteriaDto dto) {
        if (dto == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (Exception e) {
            log.error("Не удалось сериализовать criteria в JSON: {}", e.getMessage());
            throw new IllegalStateException("Ошибка сохранения критериев шаблона", e);
        }
    }

    private TemplateCriteriaDto fromJson(String json) {
        if (json == null || json.isBlank()) {
            return TemplateCriteriaDto.builder().build();
        }
        try {
            return objectMapper.readValue(json, TemplateCriteriaDto.class);
        } catch (Exception e) {
            log.warn("Не удалось разобрать criteria из БД, подставлен пустой объект: {}", e.getMessage());
            return TemplateCriteriaDto.builder().build();
        }
    }
}
