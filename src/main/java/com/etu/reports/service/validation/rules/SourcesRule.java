package com.etu.reports.service.validation.rules;

import com.etu.reports.dto.template.TemplateCriteriaDto;
import com.etu.reports.dto.validation.CriteriaResultDto;
import com.etu.reports.service.validation.internal.DocumentValidationDelegate;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Минимальное число источников в списке литературы.
 */
@Component
@Order(60)
public class SourcesRule {

    private final DocumentValidationDelegate delegate;

    public SourcesRule(DocumentValidationDelegate delegate) {
        this.delegate = delegate;
    }

    public CriteriaResultDto evaluateDocx(XWPFDocument doc, TemplateCriteriaDto criteria) {
        int n = delegate.countSourcesDocx(doc);
        int min = parseMinSources(criteria);
        return delegate.checkMinSources(n, min);
    }

    private static int parseMinSources(TemplateCriteriaDto c) {
        if (c.getMinSources() == null || c.getMinSources().isBlank()) {
            return 5;
        }
        try {
            return Integer.parseInt(c.getMinSources().trim());
        } catch (NumberFormatException e) {
            return 5;
        }
    }
}
