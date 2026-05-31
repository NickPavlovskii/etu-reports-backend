package com.etu.reports.dto.template;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

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
    @JsonProperty("tablePosition")
    @JsonAlias({"table_position"})
    private String tablePosition;
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

    public TemplateCriteriaDto() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return builder()
                .name(name)
                .description(description)
                .fileFormat(fileFormat)
                .font(font)
                .fontSize(fontSize)
                .lineSpacing(lineSpacing)
                .minPages(minPages)
                .minSources(minSources)
                .illNumbering(illNumbering)
                .figurePosition(figurePosition)
                .figureCaption(figureCaption)
                .tableTitle(tableTitle)
                .tablePosition(tablePosition)
                .tableTitlePlacement(tableTitlePlacement)
                .submissionFormat(submissionFormat)
                .hasTitlePage(hasTitlePage)
                .hasToc(hasToc)
                .hasIntroduction(hasIntroduction)
                .hasMainPart(hasMainPart)
                .hasConclusion(hasConclusion)
                .hasBibliography(hasBibliography)
                .hasAppendices(hasAppendices)
                .titlePageRequiredStrings(titlePageRequiredStrings);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getFileFormat() {
        return fileFormat;
    }

    public void setFileFormat(String fileFormat) {
        this.fileFormat = fileFormat;
    }

    public String getFont() {
        return font;
    }

    public void setFont(String font) {
        this.font = font;
    }

    public String getFontSize() {
        return fontSize;
    }

    public void setFontSize(String fontSize) {
        this.fontSize = fontSize;
    }

    public String getLineSpacing() {
        return lineSpacing;
    }

    public void setLineSpacing(String lineSpacing) {
        this.lineSpacing = lineSpacing;
    }

    public String getMinPages() {
        return minPages;
    }

    public void setMinPages(String minPages) {
        this.minPages = minPages;
    }

    public String getMinSources() {
        return minSources;
    }

    public void setMinSources(String minSources) {
        this.minSources = minSources;
    }

    public String getIllNumbering() {
        return illNumbering;
    }

    public void setIllNumbering(String illNumbering) {
        this.illNumbering = illNumbering;
    }

    public String getFigurePosition() {
        return figurePosition;
    }

    public void setFigurePosition(String figurePosition) {
        this.figurePosition = figurePosition;
    }

    public String getFigureCaption() {
        return figureCaption;
    }

    public void setFigureCaption(String figureCaption) {
        this.figureCaption = figureCaption;
    }

    public String getTableTitle() {
        return tableTitle;
    }

    public void setTableTitle(String tableTitle) {
        this.tableTitle = tableTitle;
    }

    public String getTablePosition() {
        return tablePosition;
    }

    public void setTablePosition(String tablePosition) {
        this.tablePosition = tablePosition;
    }

    public String getTableTitlePlacement() {
        return tableTitlePlacement;
    }

    public void setTableTitlePlacement(String tableTitlePlacement) {
        this.tableTitlePlacement = tableTitlePlacement;
    }

    public String getSubmissionFormat() {
        return submissionFormat;
    }

    public void setSubmissionFormat(String submissionFormat) {
        this.submissionFormat = submissionFormat;
    }

    public Boolean getHasTitlePage() {
        return hasTitlePage;
    }

    public void setHasTitlePage(Boolean hasTitlePage) {
        this.hasTitlePage = hasTitlePage;
    }

    public Boolean getHasToc() {
        return hasToc;
    }

    public void setHasToc(Boolean hasToc) {
        this.hasToc = hasToc;
    }

    public Boolean getHasIntroduction() {
        return hasIntroduction;
    }

    public void setHasIntroduction(Boolean hasIntroduction) {
        this.hasIntroduction = hasIntroduction;
    }

    public Boolean getHasMainPart() {
        return hasMainPart;
    }

    public void setHasMainPart(Boolean hasMainPart) {
        this.hasMainPart = hasMainPart;
    }

    public Boolean getHasConclusion() {
        return hasConclusion;
    }

    public void setHasConclusion(Boolean hasConclusion) {
        this.hasConclusion = hasConclusion;
    }

    public Boolean getHasBibliography() {
        return hasBibliography;
    }

    public void setHasBibliography(Boolean hasBibliography) {
        this.hasBibliography = hasBibliography;
    }

    public Boolean getHasAppendices() {
        return hasAppendices;
    }

    public void setHasAppendices(Boolean hasAppendices) {
        this.hasAppendices = hasAppendices;
    }

    public List<String> getTitlePageRequiredStrings() {
        return titlePageRequiredStrings;
    }

    public void setTitlePageRequiredStrings(List<String> titlePageRequiredStrings) {
        this.titlePageRequiredStrings = titlePageRequiredStrings;
    }

    /** Объединяет текущие критерии с фразами, извлечёнными из образца титульного листа. */
    public static TemplateCriteriaDto mergeWithTitlePageStrings(TemplateCriteriaDto current, List<String> extracted) {
        TemplateCriteriaDto base = current != null ? current : builder().build();
        return base.toBuilder()
                .hasTitlePage(true)
                .titlePageRequiredStrings(extracted)
                .build();
    }

    public static final class Builder {
        private String name;
        private String description;
        private String fileFormat;
        private String font;
        private String fontSize;
        private String lineSpacing;
        private String minPages;
        private String minSources;
        private String illNumbering;
        private String figurePosition;
        private String figureCaption;
        private String tableTitle;
        private String tablePosition;
        private String tableTitlePlacement;
        private String submissionFormat;
        private Boolean hasTitlePage;
        private Boolean hasToc;
        private Boolean hasIntroduction;
        private Boolean hasMainPart;
        private Boolean hasConclusion;
        private Boolean hasBibliography;
        private Boolean hasAppendices;
        private List<String> titlePageRequiredStrings;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder fileFormat(String fileFormat) {
            this.fileFormat = fileFormat;
            return this;
        }

        public Builder font(String font) {
            this.font = font;
            return this;
        }

        public Builder fontSize(String fontSize) {
            this.fontSize = fontSize;
            return this;
        }

        public Builder lineSpacing(String lineSpacing) {
            this.lineSpacing = lineSpacing;
            return this;
        }

        public Builder minPages(String minPages) {
            this.minPages = minPages;
            return this;
        }

        public Builder minSources(String minSources) {
            this.minSources = minSources;
            return this;
        }

        public Builder illNumbering(String illNumbering) {
            this.illNumbering = illNumbering;
            return this;
        }

        public Builder figurePosition(String figurePosition) {
            this.figurePosition = figurePosition;
            return this;
        }

        public Builder figureCaption(String figureCaption) {
            this.figureCaption = figureCaption;
            return this;
        }

        public Builder tableTitle(String tableTitle) {
            this.tableTitle = tableTitle;
            return this;
        }

        public Builder tablePosition(String tablePosition) {
            this.tablePosition = tablePosition;
            return this;
        }

        public Builder tableTitlePlacement(String tableTitlePlacement) {
            this.tableTitlePlacement = tableTitlePlacement;
            return this;
        }

        public Builder submissionFormat(String submissionFormat) {
            this.submissionFormat = submissionFormat;
            return this;
        }

        public Builder hasTitlePage(Boolean hasTitlePage) {
            this.hasTitlePage = hasTitlePage;
            return this;
        }

        public Builder hasToc(Boolean hasToc) {
            this.hasToc = hasToc;
            return this;
        }

        public Builder hasIntroduction(Boolean hasIntroduction) {
            this.hasIntroduction = hasIntroduction;
            return this;
        }

        public Builder hasMainPart(Boolean hasMainPart) {
            this.hasMainPart = hasMainPart;
            return this;
        }

        public Builder hasConclusion(Boolean hasConclusion) {
            this.hasConclusion = hasConclusion;
            return this;
        }

        public Builder hasBibliography(Boolean hasBibliography) {
            this.hasBibliography = hasBibliography;
            return this;
        }

        public Builder hasAppendices(Boolean hasAppendices) {
            this.hasAppendices = hasAppendices;
            return this;
        }

        public Builder titlePageRequiredStrings(List<String> titlePageRequiredStrings) {
            this.titlePageRequiredStrings = titlePageRequiredStrings;
            return this;
        }

        public TemplateCriteriaDto build() {
            TemplateCriteriaDto o = new TemplateCriteriaDto();
            o.setName(name);
            o.setDescription(description);
            o.setFileFormat(fileFormat);
            o.setFont(font);
            o.setFontSize(fontSize);
            o.setLineSpacing(lineSpacing);
            o.setMinPages(minPages);
            o.setMinSources(minSources);
            o.setIllNumbering(illNumbering);
            o.setFigurePosition(figurePosition);
            o.setFigureCaption(figureCaption);
            o.setTableTitle(tableTitle);
            o.setTablePosition(tablePosition);
            o.setTableTitlePlacement(tableTitlePlacement);
            o.setSubmissionFormat(submissionFormat);
            o.setHasTitlePage(hasTitlePage);
            o.setHasToc(hasToc);
            o.setHasIntroduction(hasIntroduction);
            o.setHasMainPart(hasMainPart);
            o.setHasConclusion(hasConclusion);
            o.setHasBibliography(hasBibliography);
            o.setHasAppendices(hasAppendices);
            o.setTitlePageRequiredStrings(titlePageRequiredStrings);
            return o;
        }
    }
}
