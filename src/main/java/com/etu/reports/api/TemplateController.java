package com.etu.reports.api;

import com.etu.reports.dto.template.TemplateCriteriaDto;
import com.etu.reports.dto.template.VerificationTemplateDto;
import com.etu.reports.exception.TemplateNotFoundException;
import com.etu.reports.repository.AuditLogRepository;
import com.etu.reports.repository.VerificationTemplateRepository;
import com.etu.reports.service.TitlePageTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Objects;

/**
 * API шаблонов проверки (verification_templates).
 */
@RestController
@RequestMapping("/api/templates")
@Tag(name = "Templates", description = "Шаблоны проверки документов")
public class TemplateController {

    private final VerificationTemplateRepository templateRepo;
    private final TitlePageTemplateService titlePageTemplateService;
    private final AuditLogRepository auditLogRepo;

    public TemplateController(VerificationTemplateRepository templateRepo,
                             TitlePageTemplateService titlePageTemplateService,
                             AuditLogRepository auditLogRepo) {
        this.templateRepo = templateRepo;
        this.titlePageTemplateService = titlePageTemplateService;
        this.auditLogRepo = auditLogRepo;
    }

    @Operation(summary = "Список всех шаблонов")
    @GetMapping
    public List<VerificationTemplateDto> list() {
        return templateRepo.findAll();
    }

    @Operation(summary = "Получить шаблон по ID")
    @GetMapping("/{id}")
    public VerificationTemplateDto get(@PathVariable int id) {
        return templateRepo.findById(id)
                .orElseThrow(() -> new TemplateNotFoundException(id));
    }

    @Operation(summary = "Создать шаблон")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VerificationTemplateDto create(@RequestBody CreateTemplateRequest request) {
        String name = request.getName();
        if (name == null || name.isBlank()) {
            name = "Новый шаблон";
        } else {
            name = name.trim();
        }
        VerificationTemplateDto created = templateRepo.create(
                name,
                request.getDescription(),
                request.getCriteria()
        );
        auditLogRepo.log("TEMPLATE_CREATED", "template", String.valueOf(created.getId()), null, created.getName());
        return created;
    }

    @Operation(summary = "Обновить шаблон")
    @PutMapping("/{id}")
    public VerificationTemplateDto update(@PathVariable int id, @RequestBody CreateTemplateRequest request) {
        VerificationTemplateDto existing = templateRepo.findById(id)
                .orElseThrow(() -> new TemplateNotFoundException(id));
        String name = request.getName();
        if (name == null || name.isBlank()) {
            name = existing.getName();
        } else {
            name = name.trim();
        }
        String description = request.getDescription() != null ? request.getDescription() : existing.getDescription();
        TemplateCriteriaDto criteria = request.getCriteria();
        if (criteria == null) {
            criteria = existing.getCriteria();
        } else {
            criteria = mergeTemplateCriteria(existing.getCriteria(), criteria);
        }
        VerificationTemplateDto updated = templateRepo.update(id, name, description, criteria)
                .orElseThrow(() -> new TemplateNotFoundException(id));
        auditLogRepo.log("TEMPLATE_UPDATED", "template", String.valueOf(id), null, updated.getName());
        return updated;
    }

    @Operation(summary = "Удалить шаблон")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable int id) {
        VerificationTemplateDto template = templateRepo.findById(id).orElseThrow(() -> new TemplateNotFoundException(id));
        if (!templateRepo.deleteById(id)) {
            throw new TemplateNotFoundException(id);
        }
        auditLogRepo.log("TEMPLATE_DELETED", "template", String.valueOf(id), null, template.getName());
    }

    @Operation(summary = "Загрузить макет титульного листа и извлечь обязательные элементы",
            description = "Принимает файл-шаблон (DOC, DOCX, PDF). Извлекает структуру первой страницы и сохраняет как titlePageRequiredStrings для проверки.")
    @PostMapping("/{id}/title-page-template")
    public VerificationTemplateDto uploadTitlePageTemplate(@PathVariable int id,
                                                          @RequestParam("file") MultipartFile file) {
        VerificationTemplateDto template = templateRepo.findById(id)
                .orElseThrow(() -> new TemplateNotFoundException(id));
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Файл не загружен");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) filename = "template.doc";
        List<String> extracted;
        try {
            extracted = titlePageTemplateService.extractRequiredStringsFromTemplate(filename, file.getBytes());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Ошибка чтения файла: " + e.getMessage());
        }
        if (extracted.isEmpty()) {
            throw new IllegalArgumentException("Не удалось извлечь элементы из шаблона. Проверьте формат файла.");
        }
        TemplateCriteriaDto updatedCriteria = titlePageTemplateService.mergeWithExtractedStrings(
                template.getCriteria(), extracted);
        VerificationTemplateDto updated = templateRepo.update(id, template.getName(), template.getDescription(), updatedCriteria)
                .orElseThrow(() -> new TemplateNotFoundException(id));
        auditLogRepo.log("TEMPLATE_UPDATED", "template", String.valueOf(id), null, "title-page: " + template.getName());
        return updated;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CreateTemplateRequest {
        private String name;
        private String description;
        private TemplateCriteriaDto criteria;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public TemplateCriteriaDto getCriteria() { return criteria; }
        public void setCriteria(TemplateCriteriaDto criteria) { this.criteria = criteria; }
    }

    /**
     * Частичный JSON criteria с фронта даёт null в непереданных полях — без слияния в БД затираются старые значения.
     */
    private static TemplateCriteriaDto mergeTemplateCriteria(TemplateCriteriaDto base, TemplateCriteriaDto patch) {
        if (base == null) {
            return Objects.requireNonNullElseGet(patch, () -> TemplateCriteriaDto.builder().build());
        }
        if (patch == null) {
            return base;
        }
        return base.toBuilder()
                .name(pick(patch.getName(), base.getName()))
                .description(pick(patch.getDescription(), base.getDescription()))
                .fileFormat(pick(patch.getFileFormat(), base.getFileFormat()))
                .font(pick(patch.getFont(), base.getFont()))
                .fontSize(pick(patch.getFontSize(), base.getFontSize()))
                .lineSpacing(pick(patch.getLineSpacing(), base.getLineSpacing()))
                .minPages(pick(patch.getMinPages(), base.getMinPages()))
                .minSources(pick(patch.getMinSources(), base.getMinSources()))
                .illNumbering(pick(patch.getIllNumbering(), base.getIllNumbering()))
                .figurePosition(pick(patch.getFigurePosition(), base.getFigurePosition()))
                .figureCaption(pick(patch.getFigureCaption(), base.getFigureCaption()))
                .tableTitle(pick(patch.getTableTitle(), base.getTableTitle()))
                .tablePosition(pick(patch.getTablePosition(), base.getTablePosition()))
                .tableTitlePlacement(pick(patch.getTableTitlePlacement(), base.getTableTitlePlacement()))
                .submissionFormat(pick(patch.getSubmissionFormat(), base.getSubmissionFormat()))
                .hasTitlePage(pickBool(patch.getHasTitlePage(), base.getHasTitlePage()))
                .hasToc(pickBool(patch.getHasToc(), base.getHasToc()))
                .hasIntroduction(pickBool(patch.getHasIntroduction(), base.getHasIntroduction()))
                .hasMainPart(pickBool(patch.getHasMainPart(), base.getHasMainPart()))
                .hasConclusion(pickBool(patch.getHasConclusion(), base.getHasConclusion()))
                .hasBibliography(pickBool(patch.getHasBibliography(), base.getHasBibliography()))
                .hasAppendices(pickBool(patch.getHasAppendices(), base.getHasAppendices()))
                .titlePageRequiredStrings(pickList(patch.getTitlePageRequiredStrings(), base.getTitlePageRequiredStrings()))
                .build();
    }

    private static String pick(String patch, String base) {
        return patch != null ? patch : base;
    }

    private static Boolean pickBool(Boolean patch, Boolean base) {
        return patch != null ? patch : base;
    }

    private static List<String> pickList(List<String> patch, List<String> base) {
        return patch != null ? patch : base;
    }

}
