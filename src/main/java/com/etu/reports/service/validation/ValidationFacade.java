package com.etu.reports.service.validation;

import com.etu.reports.dto.template.TemplateCriteriaDto;
import com.etu.reports.dto.validation.ValidationResultDto;
import com.etu.reports.service.validation.internal.DocumentValidationDelegate;
import org.springframework.stereotype.Service;

@Service
public class ValidationFacade {

    private final DocumentValidationDelegate documentValidationDelegate;

    public ValidationFacade(DocumentValidationDelegate documentValidationDelegate) {
        this.documentValidationDelegate = documentValidationDelegate;
    }

    public ValidationResultDto validate(String filename, byte[] content, TemplateCriteriaDto template, boolean annotate) {
        return documentValidationDelegate.validate(filename, content, template, annotate);
    }

    public ValidationResultDto validate(String filename, byte[] content, TemplateCriteriaDto template) {
        return validate(filename, content, template, false);
    }
}
