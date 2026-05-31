package com.etu.reports.api;

import com.etu.reports.dto.template.TemplateCriteriaDto;
import com.etu.reports.dto.validation.BatchValidationResultDto;
import com.etu.reports.dto.validation.ValidationResultDto;
import com.etu.reports.repository.VerificationTemplateRepository;
import com.etu.reports.service.DocumentValidationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * API валидации документов по шаблону.
 * Проверка PDF и DOCX по критериям (шрифт, объём, структура и т.д.).
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Validation", description = "Проверка документов по шаблону")
public class ValidationController {

    private final DocumentValidationService validationService;
    private final VerificationTemplateRepository templateRepo;

    public ValidationController(DocumentValidationService validationService,
                               VerificationTemplateRepository templateRepo) {
        this.validationService = validationService;
        this.templateRepo = templateRepo;
    }

    @Operation(
            summary = "Проверить документ по шаблону",
            description = "Загружает файл (PDF, DOC, DOCX) и проверяет по критериям. " +
                    "Шаблон: templateId (из БД) или template (JSON в теле)."
    )
    @ApiResponse(responseCode = "200", description = "Результат проверки")
    @PostMapping(
            value = "/validate",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ValidationResultDto validate(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "templateId", required = false) Integer templateId,
            @RequestParam(value = "annotate", required = false, defaultValue = "false") boolean annotate,
            @RequestPart(value = "template", required = false) TemplateCriteriaDto template
    ) {
        if (file == null || file.isEmpty()) {
            return ValidationResultDto.builder()
                    .valid(false)
                    .percent(0)
                    .errorMessage("Файл не загружен")
                    .build();
        }
        TemplateCriteriaDto criteria = template;
        if (templateId != null) {
            criteria = templateRepo.findById(templateId)
                    .map(t -> t.getCriteria())
                    .orElse(template);
        }

        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        byte[] content;
        try {
            content = file.getBytes();
        } catch (Exception e) {
            return ValidationResultDto.builder()
                    .valid(false)
                    .percent(0)
                    .errorMessage("Ошибка чтения файла: " + e.getMessage())
                    .build();
        }
        return validationService.validate(filename, content, criteria, annotate);
    }

    @Operation(
            summary = "Проверить несколько документов одним запросом",
            description = "Несколько файлов в multipart: повторяемое поле files, либо files[] (часть фронтов), либо повторяемое file. " +
                    "Проверки выполняются параллельно (ограниченный пул потоков). " +
                    "Шаблон: templateId (из БД) или template (JSON в теле). " +
                    "Общие поля academicYear/workType/controlType/topic — для трассировки. " +
                    "Суммарный размер тела ограничен spring.servlet.multipart.max-request-size."
    )
    @ApiResponse(responseCode = "200", description = "Результаты проверки по каждому файлу")
    @PostMapping(
            value = "/validate/batch",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public BatchValidationResultDto validateBatch(
            @RequestPart(value = "files", required = false) List<MultipartFile> boundFiles,
            HttpServletRequest request,
            @RequestParam(value = "templateId", required = false) Integer templateId,
            @RequestParam(value = "annotate", required = false, defaultValue = "false") boolean annotate,
            @RequestParam(value = "academicYear", required = false) String academicYear,
            @RequestParam(value = "workType", required = false) String workType,
            @RequestParam(value = "controlType", required = false) String controlType,
            @RequestParam(value = "topic", required = false) String topic,
            @RequestPart(value = "template", required = false) TemplateCriteriaDto template
    ) {
        TemplateCriteriaDto criteria = template;
        if (templateId != null) {
            criteria = templateRepo.findById(templateId)
                    .map(t -> t.getCriteria())
                    .orElse(template);
        }

        List<MultipartFile> files = resolveBatchMultipartFiles(boundFiles, request);
        return validationService.validateBatch(files, criteria, annotate, templateId, academicYear, workType, controlType, topic);
    }

    private static List<MultipartFile> resolveBatchMultipartFiles(List<MultipartFile> boundFiles, HttpServletRequest request) {
        List<MultipartFile> fromBinding = boundFiles != null ? new ArrayList<>(boundFiles) : new ArrayList<>();
        if (!fromBinding.isEmpty()) {
            return fromBinding;
        }
        if (!(request instanceof MultipartHttpServletRequest m)) {
            return fromBinding;
        }
        List<MultipartFile> alt = m.getFiles("files");
        if (!alt.isEmpty()) {
            return new ArrayList<>(alt);
        }
        alt = m.getFiles("files[]");
        if (!alt.isEmpty()) {
            return new ArrayList<>(alt);
        }
        alt = m.getFiles("file");
        if (!alt.isEmpty()) {
            return new ArrayList<>(alt);
        }
        return fromBinding;
    }
}
