package com.etu.reports.service.validation.rules;

import com.etu.reports.dto.template.TemplateCriteriaDto;
import com.etu.reports.dto.validation.CriteriaResultDto;
import com.etu.reports.service.validation.internal.DocumentValidationDelegate;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;


@Component
@Order(20)
public class FontRule {

    private final DocumentValidationDelegate delegate;

    public FontRule(DocumentValidationDelegate delegate) {
        this.delegate = delegate;
    }

    public CriteriaResultDto evaluateDocx(XWPFDocument doc, TemplateCriteriaDto criteria, List<XWPFRun> highlightRuns) {
        return delegate.checkFontDocx(doc, criteria.getFont(), highlightRuns);
    }

    public CriteriaResultDto evaluatePdf(DocumentValidationDelegate.PdfFontInfo fontInfo, TemplateCriteriaDto criteria) {
        return delegate.checkFontPdf(fontInfo, criteria.getFont());
    }
}
