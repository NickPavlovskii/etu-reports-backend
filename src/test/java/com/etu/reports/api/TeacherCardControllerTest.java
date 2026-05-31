package com.etu.reports.api;

import com.etu.reports.domain.DisciplineCard;
import com.etu.reports.exception.GlobalExceptionHandler;
import com.etu.reports.repository.TeachingCardRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TeacherCardController.class)
@Import(GlobalExceptionHandler.class)
class TeacherCardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TeachingCardRepository teachingCardRepository;

    @Test
    void disciplineCards_invalidYear_returns400() throws Exception {
        mockMvc.perform(get("/api/teachers/Иванов/disciplines/cards")
                        .param("academicYear", "2024"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("YYYY-YYYY")));
    }

    @Test
    void disciplineCards_valid_returnsJsonArray() throws Exception {
        DisciplineCard card = new DisciplineCard();
        card.setPlanRowId(100);
        card.setDisciplineName("Системная инженерия");
        card.setCourse(1);
        card.setSemester(2);
        card.setGroupsCount(3);
        when(teachingCardRepository.findDisciplineCardsByLastName(eq("Петров"), eq("2024-2025")))
                .thenReturn(List.of(card));

        mockMvc.perform(get("/api/teachers/Петров/disciplines/cards")
                        .param("academicYear", "2024-2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].planRowId").value(100))
                .andExpect(jsonPath("$[0].disciplineName").value("Системная инженерия"))
                .andExpect(jsonPath("$[0].course").value(1))
                .andExpect(jsonPath("$[0].semester").value(2))
                .andExpect(jsonPath("$[0].groupsCount").value(3));
    }

    @Test
    void teacherGroups_returnsList() throws Exception {
        when(teachingCardRepository.findAllGroupsByLastName("Сидоров"))
                .thenReturn(List.of("3711", "3712"));

        mockMvc.perform(get("/api/teachers/Сидоров/groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("3711"))
                .andExpect(jsonPath("$[1]").value("3712"));
    }
}
