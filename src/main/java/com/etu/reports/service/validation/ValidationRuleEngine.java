package com.etu.reports.service.validation;

import com.etu.reports.dto.template.TemplateCriteriaDto;
import com.etu.reports.dto.validation.CriteriaResultDto;
import com.etu.reports.service.validation.internal.DocumentValidationDelegate;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ValidationRuleEngine {

    private final DocumentValidationDelegate delegate;

    public ValidationRuleEngine(DocumentValidationDelegate delegate) {
        this.delegate = delegate;
    }

    public List<CriteriaResultDto> evaluateDocx(XWPFDocument doc, TemplateCriteriaDto template, List<XWPFRun> highlightRuns) {
        return delegate.collectDocxValidation(doc, template, highlightRuns);
    }

    public List<CriteriaResultDto> evaluatePdf(PDDocument doc, TemplateCriteriaDto template) throws Exception {
        return delegate.collectPdfValidation(doc, template);
    }
}
