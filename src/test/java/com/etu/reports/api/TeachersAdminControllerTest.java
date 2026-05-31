package com.etu.reports.api;

import com.etu.reports.dto.teacher.TeacherProfileDto;
import com.etu.reports.exception.GlobalExceptionHandler;
import com.etu.reports.repository.AuditLogRepository;
import com.etu.reports.repository.TeacherProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TeachersAdminController.class)
@Import(GlobalExceptionHandler.class)
class TeachersAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TeacherProfileRepository teacherProfileRepo;

    @MockBean
    private AuditLogRepository auditLogRepo;

    @Test
    void list_returnsTeachers() throws Exception {
        TeacherProfileDto dto = new TeacherProfileDto();
        dto.setLastName("Иванов");
        dto.setFio("Иванов Иван Иванович");
        dto.setRole("TEACHER");
        dto.setDisciplinesCount(2);
        when(teacherProfileRepo.findAll("ива")).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/admin/teachers").param("search", "ива"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lastName").value("Иванов"))
                .andExpect(jsonPath("$[0].role").value("TEACHER"));
    }

    @Test
    void get_existingTeacher() throws Exception {
        TeacherProfileDto dto = new TeacherProfileDto();
        dto.setLastName("Петров");
        dto.setEmail("p@etu.ru");
        when(teacherProfileRepo.findByLastName("Петров")).thenReturn(Optional.of(dto));

        mockMvc.perform(get("/api/admin/teachers/Петров"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Петров"))
                .andExpect(jsonPath("$.email").value("p@etu.ru"));
    }

    @Test
    void get_missingTeacher_returns400() throws Exception {
        when(teacherProfileRepo.findByLastName("Нет")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/admin/teachers/Нет"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Преподаватель не найден: Нет"));
    }

    @Test
    void updateRole_savesAndLogs() throws Exception {
        TeacherProfileDto updated = new TeacherProfileDto();
        updated.setLastName("Сидоров");
        updated.setRole("ADMIN");
        updated.setEmail("s@etu.ru");
        when(teacherProfileRepo.findByLastName("Сидоров")).thenReturn(Optional.of(updated));

        mockMvc.perform(put("/api/admin/teachers/Сидоров")
                        .param("actor", "Админ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"ADMIN","email":"s@etu.ru"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        verify(teacherProfileRepo).upsertRoleAndEmail(eq("Сидоров"), eq("s@etu.ru"), eq("ADMIN"));
        verify(auditLogRepo).log(eq("ROLE_CHANGED"), eq("teacher"), eq("Сидоров"), eq("Админ"), eq("role: ADMIN"));
    }
}
