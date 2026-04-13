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

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

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
    public VerificationTemplateDto create(@Valid @RequestBody CreateTemplateRequest request) {
        VerificationTemplateDto created = templateRepo.create(
                request.getName(),
                request.getDescription(),
                request.getCriteria()
        );
        auditLogRepo.log("TEMPLATE_CREATED", "template", String.valueOf(created.getId()), null, created.getName());
        return created;
    }

    @Operation(summary = "Обновить шаблон")
    @PutMapping("/{id}")
    public VerificationTemplateDto update(@PathVariable int id, @RequestBody CreateTemplateRequest request) {
        VerificationTemplateDto updated = templateRepo.update(id, request.getName(), request.getDescription(), request.getCriteria())
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

    public static class CreateTemplateRequest {
        @NotBlank(message = "Название обязательно")
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

}
