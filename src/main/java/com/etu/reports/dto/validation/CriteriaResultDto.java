package com.etu.reports.dto.validation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Результат проверки одного критерия.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CriteriaResultDto {

    /** Код критерия для фронта */
    @JsonProperty("code")
    private String code;

    /** Человекочитаемое название */
    @JsonProperty("title")
    private String title;

    /** true = пройдено, false = не пройдено */
    @JsonProperty("passed")
    private boolean passed;

    /** Сообщение (ошибка или предупреждение) */
    @JsonProperty("message")
    private String message;

    /** Ожидаемое значение (если применимо) */
    @JsonProperty("expected")
    private String expected;

    /** Фактическое значение (если применимо) */
    @JsonProperty("actual")
    private String actual;

    /** Уровень: error | warning */
    @JsonProperty("level")
    private String level;
}
