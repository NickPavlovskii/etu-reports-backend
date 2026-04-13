package com.etu.reports.dto.template;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
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
    private String illNumbering;
    @JsonProperty("figurePosition")
    private String figurePosition;
    @JsonProperty("figureCaption")
    private String figureCaption;
    @JsonProperty("tableTitle")
    private String tableTitle;
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
