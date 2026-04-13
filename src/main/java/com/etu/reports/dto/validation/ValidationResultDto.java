package com.etu.reports.dto.validation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Результат проверки документа по шаблону.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ValidationResultDto {

    /** Общий статус: true = все проверки пройдены */
    @JsonProperty("valid")
    private boolean valid;

    /** Процент соответствия (0–100) */
    @JsonProperty("percent")
    private int percent;

    /** Все результаты по критериям */
    @JsonProperty("criteria")
    private List<CriteriaResultDto> criteria;

    /** Ошибки (critical) */
    @JsonProperty("errors")
    private List<CriteriaResultDto> errors;

    /** Предупреждения */
    @JsonProperty("warnings")
    private List<CriteriaResultDto> warnings;

    /** Сообщение при неподдерживаемом формате */
    @JsonProperty("errorMessage")
    private String errorMessage;

    /** Файл с выделенными ошибками (base64), если запрошено annotate=true */
    @JsonProperty("annotatedFileBase64")
    private String annotatedFileBase64;

    /** Имя файла с замечаниями для скачивания */
    @JsonProperty("annotatedFileName")
    private String annotatedFileName;
}
