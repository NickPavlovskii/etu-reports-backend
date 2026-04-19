package com.etu.reports.service.validation.rules;

import com.etu.reports.dto.template.TemplateCriteriaDto;
import com.etu.reports.dto.validation.CriteriaResultDto;
import com.etu.reports.service.validation.internal.DocumentValidationDelegate;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Минимальное количество страниц.
 */
@Component
@Order(50)
public class PageCountRule {

    private final DocumentValidationDelegate delegate;

    public PageCountRule(DocumentValidationDelegate delegate) {
        this.delegate = delegate;
    }

    public CriteriaResultDto evaluateDocx(XWPFDocument doc, TemplateCriteriaDto criteria) {
        int pages = delegate.countPagesDocx(doc);
        int min = parseMinPages(criteria);
        return delegate.checkMinPages(pages, min);
    }

    private static int parseMinPages(TemplateCriteriaDto c) {
        if (c.getMinPages() == null || c.getMinPages().isBlank()) {
            return 5;
        }
        try {
            return Integer.parseInt(c.getMinPages().trim());
        } catch (NumberFormatException e) {
            return 5;
        }
    }
}
