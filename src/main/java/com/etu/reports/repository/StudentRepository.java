package com.etu.reports.repository;

import com.etu.reports.dto.student.StudentDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class StudentRepository {

    private final JdbcTemplate jdbc;

    public StudentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<StudentDto> findById(int id) {
        String sql = "SELECT s.lk_id AS id, s.group_name, s.record_book, " +
                "  sf.last_name, sf.first_name, sf.patronymic " +
                "FROM public.students s " +
                "LEFT JOIN public.student_fio sf ON sf.id = s.lk_id " +
                "WHERE s.lk_id = ? " +
                "ORDER BY s.academic_year DESC NULLS LAST LIMIT 1";
        return jdbc.query(sql, (rs, rowNum) -> {
            String ln = rs.getString("last_name");
            String fn = rs.getString("first_name");
            String pn = rs.getString("patronymic");
            String fio = null;
            if (ln != null || fn != null || pn != null) {
                String t = trim(ln) + " " + trim(fn) + " " + trim(pn);
                fio = t.isBlank() ? null : t.trim();
            }
            return StudentDto.builder()
                    .id(rs.getInt("id"))
                    .lastName(ln)
                    .firstName(fn)
                    .patronymic(pn)
                    .fio(fio)
                    .recordBook(rs.getString("record_book"))
                    .groupName(rs.getString("group_name"))
                    .build();
        }, id).stream().findFirst();
    }

    public void upsertFio(int id, String lastName, String firstName, String patronymic) {
        String sql = "INSERT INTO public.student_fio (id, last_name, first_name, patronymic, updated_at) " +
                "VALUES (?, NULLIF(TRIM(?), ''), NULLIF(TRIM(?), ''), NULLIF(TRIM(?), ''), CURRENT_TIMESTAMP) " +
                "ON CONFLICT (id) DO UPDATE SET " +
                "  last_name = COALESCE(NULLIF(TRIM(?), ''), student_fio.last_name), " +
                "  first_name = COALESCE(NULLIF(TRIM(?), ''), student_fio.first_name), " +
                "  patronymic = COALESCE(NULLIF(TRIM(?), ''), student_fio.patronymic), " +
                "  updated_at = CURRENT_TIMESTAMP";
        jdbc.update(sql, id, lastName, firstName, patronymic, lastName, firstName, patronymic);
    }

    public Optional<Integer> findIdByRecordBook(String recordBook) {
        if (recordBook == null || recordBook.isBlank()) return Optional.empty();
        String sql = "SELECT s.lk_id FROM public.students s " +
                "WHERE TRIM(s.record_book) = TRIM(?) " +
                "ORDER BY s.academic_year DESC NULLS LAST LIMIT 1";
        return jdbc.query(sql, (rs, rowNum) -> rs.getInt("lk_id"), recordBook.trim()).stream().findFirst();
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
