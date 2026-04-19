package com.etu.reports.dto.template;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TemplateCriteriaDto {
    @JsonProperty("name")
    private String name;
    @JsonProperty("description")
    private String description;
    @JsonProperty("fileFormat")
    private String fileFormat;
    @JsonProperty("font")
    private String font;
    @JsonProperty("fontSize")
    private String fontSize;
    @JsonProperty("lineSpacing")
    private String lineSpacing;
    @JsonProperty("minPages")
    private String minPages;
    @JsonProperty("minSources")
    private String minSources;
    @JsonProperty("illNumbering")
    @JsonAlias({"ill_numbering"})
    private String illNumbering;
    @JsonProperty("figurePosition")
    @JsonAlias({"figure_position"})
    private String figurePosition;
    @JsonProperty("figureCaption")
    @JsonAlias({"figure_caption"})
    private String figureCaption;
    @JsonProperty("tableTitle")
    @JsonAlias({"table_title"})
    private String tableTitle;
    /** Ожидаемое выравнивание заголовка таблицы: например «Справа», «По центру страницы». Проверяется в DOCX по абзацу с «Таблица …». */
    @JsonProperty("tablePosition")
    @JsonAlias({"table_position"})
    private String tablePosition;
    /**
     * Расположение названия таблицы («Таблица …») относительно таблицы: «Над таблицей» (по ГОСТ чаще всего) или «Под таблицей».
     * Для DOCX проверяется порядок блоков в теле документа; для PDF — только в составе ограничения по разметке.
     */
    @JsonProperty("tableTitlePlacement")
    @JsonAlias({"table_title_placement"})
    private String tableTitlePlacement;
    @JsonProperty("submissionFormat")
    private String submissionFormat;
    @JsonProperty("hasTitlePage")
    private Boolean hasTitlePage;
    @JsonProperty("hasToc")
    private Boolean hasToc;
    @JsonProperty("hasIntroduction")
    private Boolean hasIntroduction;
    @JsonProperty("hasMainPart")
    private Boolean hasMainPart;
    @JsonProperty("hasConclusion")
    private Boolean hasConclusion;
    @JsonProperty("hasBibliography")
    private Boolean hasBibliography;
    @JsonProperty("hasAppendices")
    private Boolean hasAppendices;
    @JsonProperty("titlePageRequiredStrings")
    private List<String> titlePageRequiredStrings;
}
