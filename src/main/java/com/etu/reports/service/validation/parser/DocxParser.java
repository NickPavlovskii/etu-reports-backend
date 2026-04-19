package com.etu.reports.service.validation.parser;

import com.etu.reports.service.validation.model.DocumentContext;
import com.etu.reports.service.validation.model.Paragraph;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Извлечение текста и метрик из DOCX для {@link DocumentContext}.
 */
@Service
public class DocxParser implements DocumentParser {

    @Override
    public DocumentContext parse(byte[] content) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(content))) {
            List<String> lines = new ArrayList<>();
            Set<String> fonts = new HashSet<>();
            Set<Double> sizes = new HashSet<>();
            for (XWPFParagraph p : doc.getParagraphs()) {
                lines.add(p.getText());
                for (XWPFRun r : p.getRuns()) {
                    if (r.getFontFamily() != null && !r.getFontFamily().isBlank()) {
                        fonts.add(r.getFontFamily().trim());
                    }
                    if (r.getFontSizeAsDouble() != null) {
                        sizes.add(r.getFontSizeAsDouble());
                    }
                }
            }
            String fullText = String.join("\n", lines);
            List<Paragraph> paras = lines.stream().map(Paragraph::new).toList();
            int pageCount = Math.max(1, (int) Math.ceil((doc.getParagraphs().size() + doc.getTables().size()) / 15.0));
            return new DocumentContext(fullText, paras, pageCount, fonts, sizes);
        }
    }
}
