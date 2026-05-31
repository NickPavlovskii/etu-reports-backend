package com.etu.reports.service;

import com.etu.reports.dto.template.KeywordMatchDto;
import com.etu.reports.dto.template.KeywordSearchResultDto;
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

@Service
public class TitlePageTemplateService {

    private static final Logger log = LoggerFactory.getLogger(TitlePageTemplateService.class);

    private static final int FIRST_PAGE_CHARS = 3500;
    private static final int MIN_STRING_LENGTH = 3;
    private static final Pattern PLACEHOLDER = Pattern.compile("^[_\\-]{2,}$|^\\s*$");
    private static final Pattern ONLY_DIGITS = Pattern.compile("^\\d+$");
    private static final int DEFAULT_MAX_KEYWORD_MATCHES = 30;
    private static final int EXCERPT_RADIUS = 60;

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

    /** Полный текст DOCX/DOC для поиска ключевых фраз в UI шаблона. */
    public String extractFullTextFromFile(String filename, byte[] content) {
        String ext = getExtension(filename).toLowerCase();
        try {
            if (".pdf".equals(ext)) {
                return extractFullTextPdf(content);
            }
            if (".docx".equals(ext) || ".doc".equals(ext)) {
                return extractFullTextWord(content, ext);
            }
            throw new IllegalArgumentException("Неподдерживаемый формат: " + ext + ". Допустимы: .doc, .docx, .pdf");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Ошибка извлечения текста: {}", e.getMessage());
            throw new IllegalArgumentException("Не удалось прочитать файл: " + e.getMessage());
        }
    }

    public KeywordSearchResultDto searchKeywords(String filename, byte[] content, String query, int maxMatches) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Укажите ключевое слово для поиска");
        }
        int limit = maxMatches <= 0 ? DEFAULT_MAX_KEYWORD_MATCHES : Math.min(maxMatches, 100);
        String text = extractFullTextFromFile(filename, content);
        List<KeywordMatchDto> matches = findKeywordMatches(text, query, limit);
        return new KeywordSearchResultDto(
                filename != null ? filename : "file",
                query.trim(),
                matches
        );
    }

    public List<KeywordMatchDto> findKeywordMatches(String text, String query, int maxMatches) {
        if (text == null || text.isBlank() || query == null || query.isBlank()) {
            return List.of();
        }
        String q = query.trim();
        String lowerText = text.toLowerCase(Locale.ROOT);
        String lowerQuery = q.toLowerCase(Locale.ROOT);
        String[] lines = text.split("\\R");
        List<KeywordMatchDto> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (int i = 0; i < lines.length && out.size() < maxMatches; i++) {
            String line = lines[i];
            if (line == null || line.isBlank()) {
                continue;
            }
            String lowerLine = line.toLowerCase(Locale.ROOT);
            int from = 0;
            while (from < lowerLine.length() && out.size() < maxMatches) {
                int idx = lowerLine.indexOf(lowerQuery, from);
                if (idx < 0) {
                    break;
                }
                String fragment = line.substring(idx, Math.min(line.length(), idx + q.length())).trim();
                if (fragment.isBlank()) {
                    from = idx + 1;
                    continue;
                }
                String key = (i + 1) + "|" + fragment.toLowerCase(Locale.ROOT);
                if (seen.add(key)) {
                    out.add(new KeywordMatchDto(fragment, buildExcerpt(line, idx, q.length()), i + 1));
                }
                from = idx + lowerQuery.length();
            }
        }
        return out;
    }

    private String buildExcerpt(String line, int matchStart, int matchLength) {
        int start = Math.max(0, matchStart - EXCERPT_RADIUS);
        int end = Math.min(line.length(), matchStart + matchLength + EXCERPT_RADIUS);
        String excerpt = line.substring(start, end).trim();
        if (start > 0) {
            excerpt = "…" + excerpt;
        }
        if (end < line.length()) {
            excerpt = excerpt + "…";
        }
        return excerpt;
    }

    private String extractFullTextWord(byte[] content, String ext) throws Exception {
        try (InputStream is = new ByteArrayInputStream(content)) {
            if (".docx".equals(ext)) {
                return extractFullTextDocx(is);
            }
            try (HWPFDocument doc = new HWPFDocument(is);
                 WordExtractor extractor = new WordExtractor(doc)) {
                return extractor.getText();
            }
        }
    }

    private String extractFullTextDocx(InputStream is) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (XWPFDocument doc = new XWPFDocument(is)) {
            for (XWPFParagraph p : doc.getParagraphs()) {
                String t = p.getText();
                if (t != null) {
                    sb.append(t).append('\n');
                }
            }
            for (XWPFTable table : doc.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        for (XWPFParagraph p : cell.getParagraphs()) {
                            String t = p.getText();
                            if (t != null) {
                                sb.append(t).append('\n');
                            }
                        }
                    }
                }
            }
        }
        return sb.toString();
    }

    /** Текст первой страницы DOCX для проверки ключевых фраз титульного листа. */
    public String extractFirstPageTextFromDocx(XWPFDocument doc) {
        StringBuilder sb = new StringBuilder();
        for (XWPFParagraph p : doc.getParagraphs()) {
            String t = p.getText();
            if (t != null) {
                sb.append(t).append('\n');
            }
            if (sb.length() >= FIRST_PAGE_CHARS) {
                break;
            }
        }
        if (sb.length() < FIRST_PAGE_CHARS) {
            for (XWPFTable t : doc.getTables()) {
                for (XWPFTableRow row : t.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        for (XWPFParagraph p : cell.getParagraphs()) {
                            String text = p.getText();
                            if (text != null) {
                                sb.append(text).append('\n');
                            }
                        }
                    }
                    if (sb.length() >= FIRST_PAGE_CHARS) {
                        break;
                    }
                }
                if (sb.length() >= FIRST_PAGE_CHARS) {
                    break;
                }
            }
        }
        return truncateToFirstPage(sb.toString());
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

    private String extractFullTextPdf(byte[] content) throws Exception {
        try (PDDocument doc = Loader.loadPDF(content)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setAddMoreFormatting(true);
            return stripper.getText(doc);
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
}
