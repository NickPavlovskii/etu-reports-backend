package com.etu.reports.service;

import com.etu.reports.dto.template.TemplateCriteriaDto;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Извлечение обязательных элементов титульного листа из файла-шаблона
 * (DOC, DOCX, PDF). Используется для настройки titlePageRequiredStrings.
 */
@Service
public class TitlePageTemplateService {

    private static final Logger log = LoggerFactory.getLogger(TitlePageTemplateService.class);

    private static final int FIRST_PAGE_CHARS = 3500;
    private static final int MIN_STRING_LENGTH = 3;
    private static final Pattern PLACEHOLDER = Pattern.compile("^[_\\-]{2,}$|^\\s*$");
    private static final Pattern ONLY_DIGITS = Pattern.compile("^\\d+$");

    /**
     * Извлекает обязательные строки для титульного листа из первого листа/страницы файла.
     * Поддерживаются: .doc, .docx, .pdf
     *
     * @param filename имя файла (для определения формата)
     * @param content  содержимое файла
     * @return список строк, которые должны присутствовать на титульном листе
     */
    public List<String> extractRequiredStringsFromTemplate(String filename, byte[] content) {
        String ext = getExtension(filename).toLowerCase();
        String firstPageText;
        try {
            if (".pdf".equals(ext)) {
                firstPageText = extractFirstPagePdf(content);
            } else if (".docx".equals(ext) || ".doc".equals(ext)) {
                firstPageText = extractFirstPageWord(content, ext);
            } else {
                throw new IllegalArgumentException("Неподдерживаемый формат: " + ext + ". Допустимы: .doc, .docx, .pdf");
            }
        } catch (Exception e) {
            log.warn("Ошибка извлечения из шаблона титульного листа: {}", e.getMessage());
            throw new IllegalArgumentException("Не удалось прочитать шаблон: " + e.getMessage());
        }
        return parseRequiredStrings(firstPageText);
    }

    /**
     * Парсит текст первой страницы в список обязательных строк.
     * - Строки вида "Метка: значение" → берётся "Метка"
     * - Остальные значимые строки → целиком
     * - Пустые, только цифры, плейсхолдеры (___ ) — пропускаются
     */
    public List<String> parseRequiredStrings(String firstPageText) {
        if (firstPageText == null || firstPageText.isBlank()) return List.of();

        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        String[] lines = firstPageText.split("[\r\n]+");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.length() < MIN_STRING_LENGTH) continue;
            if (PLACEHOLDER.matcher(trimmed).matches()) continue;
            if (ONLY_DIGITS.matcher(trimmed).matches()) continue;

            String key;
            int colonIdx = trimmed.indexOf(':');
            if (colonIdx > 0 && colonIdx < 50) {
                key = trimmed.substring(0, colonIdx).trim();
                if (key.length() >= MIN_STRING_LENGTH) {
                    if (seen.add(normalize(key))) result.add(key);
                }
            } else {
                key = trimmed;
                if (key.length() <= 100 && seen.add(normalize(key))) {
                    result.add(key);
                }
            }
        }

        log.debug("Извлечено {} обязательных строк из шаблона титульного листа", result.size());
        return result;
    }

    private String normalize(String s) {
        return s.toLowerCase().replaceAll("\\s+", " ").trim();
    }

    private String extractFirstPageWord(byte[] content, String ext) throws Exception {
        try (InputStream is = new ByteArrayInputStream(content)) {
            if (".docx".equals(ext)) {
                return extractFirstPageDocx(is);
            } else {
                return extractFirstPageDoc(is);
            }
        }
    }

    private String extractFirstPageDocx(InputStream is) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (XWPFDocument doc = new XWPFDocument(is)) {
            for (XWPFParagraph p : doc.getParagraphs()) {
                String t = p.getText();
                if (t != null) sb.append(t).append("\n");
                if (sb.length() >= FIRST_PAGE_CHARS) break;
            }
            if (sb.length() < FIRST_PAGE_CHARS) {
                for (XWPFTable t : doc.getTables()) {
                    for (XWPFTableRow row : t.getRows()) {
                        for (XWPFTableCell cell : row.getTableCells()) {
                            for (XWPFParagraph p : cell.getParagraphs()) {
                                String text = p.getText();
                                if (text != null) sb.append(text).append("\n");
                            }
                        }
                        if (sb.length() >= FIRST_PAGE_CHARS) break;
                    }
                    if (sb.length() >= FIRST_PAGE_CHARS) break;
                }
            }
        }
        return truncateToFirstPage(sb.toString());
    }

    private String extractFirstPageDoc(InputStream is) throws Exception {
        try (HWPFDocument doc = new HWPFDocument(is);
             WordExtractor ext = new WordExtractor(doc)) {
            String full = ext.getText();
            return truncateToFirstPage(full);
        }
    }

    private String extractFirstPagePdf(byte[] content) throws Exception {
        try (PDDocument doc = Loader.loadPDF(content)) {
            if (doc.getNumberOfPages() < 1) return "";
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setAddMoreFormatting(true);
            stripper.setStartPage(1);
            stripper.setEndPage(1);
            return stripper.getText(doc);
        }
    }

    private String truncateToFirstPage(String text) {
        if (text == null) return "";
        if (text.length() <= FIRST_PAGE_CHARS) return text;
        return text.substring(0, FIRST_PAGE_CHARS);
    }

    private String getExtension(String filename) {
        if (filename == null) return "";
        int i = filename.lastIndexOf('.');
        return i >= 0 ? filename.substring(i) : "";
    }

    /**
     * Строит обновлённые критерии с добавленным titlePageRequiredStrings из шаблона.
     */
    public TemplateCriteriaDto mergeWithExtractedStrings(TemplateCriteriaDto current, List<String> extracted) {
        if (current == null) current = TemplateCriteriaDto.builder().build();
        return TemplateCriteriaDto.builder()
                .name(current.getName())
                .description(current.getDescription())
                .fileFormat(current.getFileFormat())
                .font(current.getFont())
                .fontSize(current.getFontSize())
                .lineSpacing(current.getLineSpacing())
                .minPages(current.getMinPages())
                .minSources(current.getMinSources())
                .illNumbering(current.getIllNumbering())
                .figurePosition(current.getFigurePosition())
                .figureCaption(current.getFigureCaption())
                .tableTitle(current.getTableTitle())
                .submissionFormat(current.getSubmissionFormat())
                .hasTitlePage(true)
                .titlePageRequiredStrings(extracted)
                .hasToc(current.getHasToc())
                .hasIntroduction(current.getHasIntroduction())
                .hasMainPart(current.getHasMainPart())
                .hasConclusion(current.getHasConclusion())
                .hasBibliography(current.getHasBibliography())
                .hasAppendices(current.getHasAppendices())
                .build();
    }
}
