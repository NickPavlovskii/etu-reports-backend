package com.etu.reports.service.validation.parser;

import com.etu.reports.service.validation.model.DocumentContext;
import com.etu.reports.service.validation.model.Paragraph;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Извлечение текста из PDF для {@link DocumentContext} (шрифты/кегль — в {@link com.etu.reports.service.validation.internal.DocumentValidationDelegate#extractPdfFontInfo}).
 */
@Service
public class PdfParser implements DocumentParser {

    @Override
    public DocumentContext parse(byte[] content) throws Exception {
        try (PDDocument doc = Loader.loadPDF(content)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            List<Paragraph> paras = Arrays.stream(text.split("\n")).map(Paragraph::new).toList();
            return new DocumentContext(text, paras, doc.getNumberOfPages(), new HashSet<>(), new HashSet<>());
        }
    }
}
