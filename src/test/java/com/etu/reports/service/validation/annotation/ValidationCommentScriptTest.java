package com.etu.reports.service.validation.annotation;

import com.etu.reports.dto.validation.CriteriaResultDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationCommentScriptTest {

    @Test
    void forCriterion_fontUsesScriptedPrefix() {
        CriteriaResultDto c = CriteriaResultDto.builder()
                .code("FONT")
                .title("Шрифт")
                .passed(false)
                .message("Обнаружен другой шрифт")
                .expected("Times New Roman")
                .actual("Arial")
                .build();
        String text = ValidationCommentScript.forCriterion(c);
        assertThat(text).startsWith("[Автопроверка]");
        assertThat(text).contains("Times New Roman");
        assertThat(text).contains("Arial");
    }
}
