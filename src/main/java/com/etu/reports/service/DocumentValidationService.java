package com.etu.reports.service;

import com.etu.reports.config.ValidationBatchConfig;
import com.etu.reports.dto.template.TemplateCriteriaDto;
import com.etu.reports.dto.validation.BatchValidationResultDto;
import com.etu.reports.dto.validation.ValidationResultDto;
import com.etu.reports.service.validation.ValidationFacade;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Совместимость: фасад проверки документов по шаблону.
 * Реализация — {@link ValidationFacade}.
 */
@Service
public class DocumentValidationService {

    /** Максимум файлов в одном batch-запросе (защита от перегрузки памяти/CPU). */
    public static final int MAX_BATCH_FILES = 50;

    private final ValidationFacade validationFacade;
    private final Executor validationBatchExecutor;

    public DocumentValidationService(
            ValidationFacade validationFacade,
            @Qualifier(ValidationBatchConfig.EXECUTOR_BEAN_NAME) Executor validationBatchExecutor) {
        this.validationFacade = validationFacade;
        this.validationBatchExecutor = validationBatchExecutor;
    }

    public ValidationResultDto validate(String filename, byte[] content, TemplateCriteriaDto template, boolean annotate) {
        return validationFacade.validate(filename, content, template, annotate);
    }

    public ValidationResultDto validate(String filename, byte[] content, TemplateCriteriaDto template) {
        return validationFacade.validate(filename, content, template, false);
    }

    /**
     * Пакетная проверка: критерии шаблона общие для всех файлов; проверки выполняются параллельно
     * (число одновременных задач ограничено пулом {@link ValidationBatchConfig}).
     */
    public BatchValidationResultDto validateBatch(
            List<MultipartFile> files,
            TemplateCriteriaDto criteria,
            boolean annotate,
            Integer templateId,
            String academicYear,
            String workType,
            String controlType,
            String topic) {

        if (files == null) {
            files = List.of();
        }
        if (files.size() > MAX_BATCH_FILES) {
            return BatchValidationResultDto.builder()
                    .academicYear(academicYear)
                    .workType(workType)
                    .controlType(controlType)
                    .topic(topic)
                    .templateId(templateId)
                    .annotate(annotate)
                    .error("Слишком много файлов в одном запросе: максимум " + MAX_BATCH_FILES)
                    .results(List.of())
                    .build();
        }
        if (files.isEmpty()) {
            return BatchValidationResultDto.builder()
                    .academicYear(academicYear)
                    .workType(workType)
                    .controlType(controlType)
                    .topic(topic)
                    .templateId(templateId)
                    .annotate(annotate)
                    .error("Не переданы файлы для проверки. Ожидается multipart-поле files (повторяемое), files[] или file")
                    .results(List.of())
                    .build();
        }

        List<CompletableFuture<BatchValidationResultDto.Item>> futures = new ArrayList<>(files.size());
        for (MultipartFile file : files) {
            MultipartFile mf = file;
            futures.add(CompletableFuture.supplyAsync(() -> validateBatchItem(mf, criteria, annotate), validationBatchExecutor));
        }
        List<BatchValidationResultDto.Item> out = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        return BatchValidationResultDto.builder()
                .academicYear(academicYear)
                .workType(workType)
                .controlType(controlType)
                .topic(topic)
                .templateId(templateId)
                .annotate(annotate)
                .results(out)
                .build();
    }

    private BatchValidationResultDto.Item validateBatchItem(MultipartFile file, TemplateCriteriaDto criteria, boolean annotate) {
        String filename = (file != null && file.getOriginalFilename() != null) ? file.getOriginalFilename() : "file";
        if (file == null || file.isEmpty()) {
            return BatchValidationResultDto.Item.builder()
                    .filename(filename)
                    .result(ValidationResultDto.builder()
                            .valid(false)
                            .percent(0)
                            .errorMessage("Файл не загружен")
                            .build())
                    .build();
        }
        try {
            ValidationResultDto r = validationFacade.validate(filename, file.getBytes(), criteria, annotate);
            return BatchValidationResultDto.Item.builder().filename(filename).result(r).build();
        } catch (Exception e) {
            return BatchValidationResultDto.Item.builder()
                    .filename(filename)
                    .result(ValidationResultDto.builder()
                            .valid(false)
                            .percent(0)
                            .errorMessage("Ошибка чтения файла: " + e.getMessage())
                            .build())
                    .build();
        }
    }
}
