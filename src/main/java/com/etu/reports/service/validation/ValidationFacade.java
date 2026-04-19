package com.etu.reports.service.validation;

import com.etu.reports.dto.template.TemplateCriteriaDto;
import com.etu.reports.dto.validation.ValidationResultDto;
import com.etu.reports.service.validation.internal.DocumentValidationDelegate;
import org.springframework.stereotype.Service;

/**
 * Оркестратор проверки: точка входа для {@link com.etu.reports.service.DocumentValidationService}.
 * <p>
 * Цепочка: {@link ValidationFacade} → {@link DocumentValidationDelegate}
 * (парсинг внутри delegate / POI-PDFBox; {@link com.etu.reports.service.validation.parser.DocxParser} и
 * {@link com.etu.reports.service.validation.parser.PdfParser} строят {@link com.etu.reports.service.validation.model.DocumentContext} для единого представления).
 * Правила агрегируются в {@link ValidationRuleEngine} и {@link DocumentValidationDelegate#collectDocxValidation}.
 */
@Service
public class ValidationFacade {

    private final DocumentValidationDelegate documentValidationDelegate;

    public ValidationFacade(DocumentValidationDelegate documentValidationDelegate) {
        this.documentValidationDelegate = documentValidationDelegate;
    }

    /**
     * Полная проверка файла по шаблону (DOC/DOCX/PDF), с опциональной аннотацией ошибок.
     */
    public ValidationResultDto validate(String filename, byte[] content, TemplateCriteriaDto template, boolean annotate) {
        return documentValidationDelegate.validate(filename, content, template, annotate);
    }

    public ValidationResultDto validate(String filename, byte[] content, TemplateCriteriaDto template) {
        return validate(filename, content, template, false);
    }
}
