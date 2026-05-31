package com.etu.reports.api;

import com.etu.reports.dto.student.StudentDto;
import com.etu.reports.exception.GlobalExceptionHandler;
import com.etu.reports.repository.StudentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = StudentController.class)
@Import(GlobalExceptionHandler.class)
class StudentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StudentRepository studentRepository;

    @Test
    void get_existingStudent_returns200() throws Exception {
        StudentDto dto = StudentDto.builder()
                .id(42)
                .fio("Иванов Иван Иванович")
                .groupName("3711")
                .build();
        when(studentRepository.findById(42)).thenReturn(Optional.of(dto));

        mockMvc.perform(get("/api/students/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.fio").value("Иванов Иван Иванович"))
                .andExpect(jsonPath("$.groupName").value("3711"));
    }

    @Test
    void get_missingStudent_returns400() throws Exception {
        when(studentRepository.findById(99)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/students/99"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Студент не найден: id=99"));
    }
}
