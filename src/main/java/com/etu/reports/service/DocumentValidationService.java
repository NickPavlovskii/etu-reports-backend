package com.etu.reports.service;

import com.etu.reports.dto.template.TemplateCriteriaDto;
import com.etu.reports.dto.validation.CriteriaResultDto;
import com.etu.reports.dto.validation.ValidationResultDto;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.poi.xwpf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.Base64;

/**
 * Сервис проверки документов (DOCX, PDF) по шаблону критериев.
 * Поддерживает выделение ошибок в документе (annotate=true).
 */
@Service
public class DocumentValidationService {

    private static final Logger log = LoggerFactory.getLogger(DocumentValidationService.class);

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".doc", ".docx", ".pdf");
    /** ГОСТ-стиль: 1. Автор. Название — Город; [1]. ...; Иванов И.И. ...; 1. ... [Электронный ресурс] */
    private static final Pattern BIBLIOGRAPHY_PATTERN = Pattern.compile(
            "\\d+\\.\\s*[А-Яа-яA-Za-z].*[.,]\\s*[—\\-–].*|" +
            "\\d+\\.\\s*[А-Яа-яA-Za-z].*\\d{4}[\\s.).]|" +
            "[А-Яа-я]+\\s+[А-Я]\\.\\s*[А-Я]\\.?\\s+.*\\d{4}|" +
            "\\[\\d+\\].*[.,]\\s*[—\\-–].*|" +
            "\\[\\d+\\][^\\]]{15,}|" +
            "\\d+\\.\\s+[\\s\\S]+?\\[Электронный ресурс\\]|" +
            "\\d+\\.\\s+[\\s\\S]+?\\[Electronic resource\\]",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
    );
    /** Нумерованные записи: 1. 2. 3. или 1 2 3 (отдельная ячейка таблицы) */
    private static final Pattern NUMBERED_ENTRY_PATTERN = Pattern.compile("(?m)^\\s*(\\d{1,3})\\.?(?:\\s|$)");
    private static final Pattern SECTION_HEADINGS = Pattern.compile(
            "(?i)(содержание|оглавление|введение|заключение|список\\s+литературы|библиограф|приложения?)",
            Pattern.UNICODE_CASE
    );

    private static final String HIGHLIGHT_COLOR = "FF0000"; // красный для ошибок

    /**
     * Валидирует документ по критериям шаблона.
     *
     * @param filename  исходное имя файла
     * @param content   содержимое файла
     * @param template  критерии шаблона
     * @param annotate  если true — вернёт файл с выделенными ошибками
     */
    public ValidationResultDto validate(String filename, byte[] content, TemplateCriteriaDto template, boolean annotate) {
        if (template == null) template = defaultTemplate();

        String ext = getExtension(filename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            return ValidationResultDto.builder()
                    .valid(false).percent(0)
                    .errorMessage("Неподдерживаемый формат. Допустимы: .doc, .docx, .pdf")
                    .build();
        }

        try {
            if (".pdf".equals(ext)) return validatePdf(content, template, filename, annotate);
            if (".docx".equals(ext) || ".doc".equals(ext)) return validateDocx(content, template, filename, annotate);
        } catch (Exception e) {
            log.warn("Validation error for {}: {}", filename, e.getMessage());
            return ValidationResultDto.builder().valid(false).percent(0)
                    .errorMessage("Ошибка при чтении документа: " + e.getMessage()).build();
        }
        return ValidationResultDto.builder().valid(false).percent(0).errorMessage("Формат не поддерживается").build();
    }

    /** Совместимость: без аннотаций */
    public ValidationResultDto validate(String filename, byte[] content, TemplateCriteriaDto template) {
        return validate(filename, content, template, false);
    }

    private ValidationResultDto validateDocx(byte[] content, TemplateCriteriaDto template, String filename, boolean annotate) throws Exception {
        List<CriteriaResultDto> results = new ArrayList<>();
        List<XWPFRun> runsToHighlight = new ArrayList<>();
        List<String> reportRows = new ArrayList<>();

        try (InputStream is = new java.io.ByteArrayInputStream(content);
             XWPFDocument doc = new XWPFDocument(is)) {

            results.add(criteriaFileFormat(true, template.getFileFormat()));

            CriteriaResultDto fontResult = checkFontDocx(doc, template.getFont(), runsToHighlight);
            results.add(fontResult);
            if (!fontResult.isPassed()) reportRows.add("Шрифт: " + fontResult.getMessage());

            CriteriaResultDto sizeResult = checkFontSizeDocx(doc, template.getFontSize(), runsToHighlight);
            results.add(sizeResult);
            if (!sizeResult.isPassed()) reportRows.add("Размер шрифта: " + sizeResult.getMessage());

            CriteriaResultDto spacingResult = checkLineSpacingDocx(doc, template.getLineSpacing(), runsToHighlight);
            results.add(spacingResult);
            if (!spacingResult.isPassed()) reportRows.add("Межстрочный интервал: " + spacingResult.getMessage());

            int pageCount = countPagesDocx(doc);
            CriteriaResultDto pagesResult = checkMinPages(pageCount, parseInt(template.getMinPages(), 5));
            results.add(pagesResult);
            if (!pagesResult.isPassed()) reportRows.add(pagesResult.getMessage());

            int sourcesCount = countSourcesDocx(doc);
            CriteriaResultDto sourcesResult = checkMinSources(sourcesCount, parseInt(template.getMinSources(), 5));
            results.add(sourcesResult);
            if (!sourcesResult.isPassed()) reportRows.add(sourcesResult.getMessage());

            List<CriteriaResultDto> structureResults = checkStructureDocx(doc, template);
            results.addAll(structureResults);
            structureResults.stream().filter(r -> !r.isPassed())
                    .forEach(r -> reportRows.add(r.getTitle() + ": " + r.getMessage()));

            if (annotate && (!runsToHighlight.isEmpty() || !reportRows.isEmpty())) {
                for (XWPFRun r : runsToHighlight) {
                    try { r.setColor(HIGHLIGHT_COLOR); } catch (Exception ignored) {}
                }
                byte[] annotated = writeDocx(doc);
                String base64 = Base64.getEncoder().encodeToString(annotated);
                String outName = filename.replaceFirst("(\\.docx?)$", "_замечания$1");
                if (outName.equals(filename)) outName = "document_замечания.docx";
                return buildResult(results).toBuilder()
                        .annotatedFileBase64(base64)
                        .annotatedFileName(outName)
                        .build();
            }
        }
        return buildResult(results);
    }

    private byte[] writeDocx(XWPFDocument doc) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        doc.write(out);
        return out.toByteArray();
    }

    private ValidationResultDto validatePdf(byte[] content, TemplateCriteriaDto template, String filename, boolean annotate) throws Exception {
        List<CriteriaResultDto> results = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(content)) {
            results.add(criteriaFileFormat(true, template.getFileFormat()));
            PdfFontInfo fontInfo = null;
            try { fontInfo = extractPdfFontInfo(doc); } catch (Exception ignored) {}
            results.add(checkFontPdf(fontInfo, template.getFont()));
            results.add(checkFontSizePdf(fontInfo, template.getFontSize()));
            results.add(checkLineSpacingPdf(doc, template.getLineSpacing()));
            int pageCount = doc.getNumberOfPages();
            results.add(checkMinPages(pageCount, parseInt(template.getMinPages(), 5)));
            int sourcesCount = countSourcesPdf(doc);
            results.add(checkMinSources(sourcesCount, parseInt(template.getMinSources(), 5)));
            results.addAll(checkStructurePdf(doc, template));

            List<CriteriaResultDto> failed = results.stream().filter(r -> !r.isPassed()).collect(Collectors.toList());
            if (annotate && !failed.isEmpty()) {
                byte[] annotated = writePdf(doc);
                String base64 = Base64.getEncoder().encodeToString(annotated);
                String outName = filename.replaceFirst("(\\.pdf)$", "_замечания$1");
                if (outName.equals(filename)) outName = "document_замечания.pdf";
                return buildResult(results).toBuilder()
                        .annotatedFileBase64(base64)
                        .annotatedFileName(outName)
                        .build();
            }
        }
        return buildResult(results);
    }

    private byte[] writePdf(PDDocument doc) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        doc.save(out);
        return out.toByteArray();
    }

    private CriteriaResultDto criteriaFileFormat(boolean ok, String expected) {
        return CriteriaResultDto.builder()
                .code("FILE_FORMAT")
                .title("Формат файла")
                .passed(ok)
                .message(ok ? "Формат соответствует" : "Неверный формат")
                .expected(expected)
                .level(ok ? null : "error")
                .build();
    }

    /** Заголовки, подписи рисунков и таблиц — не проверяем шрифт/размер/интервал */
    private static final Pattern HEADING_STYLE = Pattern.compile("(?i)(heading|заголовок|title|caption|toc|оглавление|заголовок\\s*\\d|heading\\s*\\d)");
    private static final Pattern FIGURE_CAPTION = Pattern.compile("(?i)^\\s*(рис\\.?|рисунок|figure|fig\\.)\\s*\\d");
    private static final Pattern TABLE_CAPTION = Pattern.compile("(?i)^\\s*(таблица|table)\\s*\\d");
    /** Извлечение номера из подписи рисунка: "Рис. 1" -> 1 */
    private static final Pattern FIGURE_NUMBER = Pattern.compile("(?i)(?:рис\\.?|рисунок|figure|fig\\.)\\s*(\\d+)");
    /** Извлечение номера из заголовка таблицы: "Таблица 1" -> 1 */
    private static final Pattern TABLE_NUMBER = Pattern.compile("(?i)(?:таблица|table)\\s*(\\d+)");
    /** Заголовки разделов — только в начале строки (с опциональным номером) */
    private static final Pattern SECTION_HEADING = Pattern.compile("(?i)^\\s*(\\d+(\\.\\d+)*\\.?\\s*)?(содержание|оглавление|введение|заключение|список\\s+(использованных\\s+)?источников|список\\s+литературы|приложени)\\b");
    /** Нумерованные заголовки: 1. 2.1 3.2.1 и т.д. в начале строки */
    private static final Pattern NUMBERED_HEADING = Pattern.compile("^\\s*\\d+(\\.\\d+)*\\.?\\s*[А-Яа-яA-Z]");

    /** Пропуск проверки шрифта/размера/интервала для заголовков, таблиц, подписей. */
    private boolean shouldSkipFormattingCheck(XWPFParagraph p, XWPFParagraph previous) {
        String style = p.getStyle();
        if (style != null && !style.isBlank() && HEADING_STYLE.matcher(style).find()) return true;
        String text = p.getText();
        if (text != null && !text.isBlank()) {
            String t = text.trim();
            if (SECTION_HEADING.matcher(t).find()) return true;
            if (FIGURE_CAPTION.matcher(t).find() || TABLE_CAPTION.matcher(t).find()) return true;
            if (NUMBERED_HEADING.matcher(t).find() && t.length() < 150) return true;
            if (previous != null) {
                String prevText = previous.getText();
                if (prevText != null && !prevText.isBlank()) {
                    String prev = prevText.trim();
                    if ((FIGURE_CAPTION.matcher(prev).find() || TABLE_CAPTION.matcher(prev).find())
                            && t.length() < 120) return true;
                }
            }
        }
        return false;
    }

    private CriteriaResultDto checkFontDocx(XWPFDocument doc, String expectedFont, List<XWPFRun> toHighlight) {
        if (expectedFont == null || expectedFont.isBlank()) return passed("FONT", "Шрифт", "Проверка не задана");
        String normalizedExpected = normalizeFont(expectedFont);
        List<XWPFRun> wrongRuns = new ArrayList<>();
        List<XWPFParagraph> paragraphs = doc.getParagraphs();
        for (int i = 0; i < paragraphs.size(); i++) {
            XWPFParagraph p = paragraphs.get(i);
            XWPFParagraph prev = i > 0 ? paragraphs.get(i - 1) : null;
            if (shouldSkipFormattingCheck(p, prev)) continue;
            for (XWPFRun r : p.getRuns()) {
                String f = r.getFontFamily();
                if (f != null && !f.isBlank()) {
                    if (!normalizeFont(f).contains(normalizedExpected) && !normalizedExpected.contains(normalizeFont(f))) {
                        wrongRuns.add(r);
                    }
                }
            }
        }
        // Таблицы не проверяем — в них могут быть подписи, заголовки и т.д.
        boolean ok = wrongRuns.isEmpty();
        if (ok) {
            Set<String> fonts = new HashSet<>();
            for (int i = 0; i < paragraphs.size(); i++) {
                if (shouldSkipFormattingCheck(paragraphs.get(i), i > 0 ? paragraphs.get(i - 1) : null)) continue;
                XWPFParagraph p = paragraphs.get(i);
                for (XWPFRun r : p.getRuns()) {
                    String f = r.getFontFamily();
                    if (f != null && !f.isBlank()) fonts.add(f.trim());
                }
            }
            if (fonts.isEmpty()) ok = true;
        }
        if (!ok) toHighlight.addAll(wrongRuns);
        Set<String> allFonts = new HashSet<>();
        for (int i = 0; i < paragraphs.size(); i++) {
            if (shouldSkipFormattingCheck(paragraphs.get(i), i > 0 ? paragraphs.get(i - 1) : null)) continue;
            XWPFParagraph p = paragraphs.get(i);
            for (XWPFRun r : p.getRuns()) {
                String f = r.getFontFamily();
                if (f != null && !f.isBlank()) allFonts.add(f.trim());
            }
        }
        return CriteriaResultDto.builder()
                .code("FONT").title("Шрифт").passed(ok)
                .message(ok ? "Шрифт соответствует" : "Обнаружен шрифт, отличный от " + expectedFont)
                .expected(expectedFont).actual(allFonts.isEmpty() ? "не определён" : String.join(", ", allFonts))
                .level(ok ? null : "warning").build();
    }

    private CriteriaResultDto checkFontSizeDocx(XWPFDocument doc, String expectedSize, List<XWPFRun> toHighlight) {
        if (expectedSize == null || expectedSize.isBlank()) return passed("FONT_SIZE", "Размер шрифта", "Проверка не задана");
        double expectedPt = parseFontSize(expectedSize);
        if (expectedPt <= 0) return passed("FONT_SIZE", "Размер шрифта", "Критерий не распознан");
        List<XWPFRun> wrongRuns = new ArrayList<>();
        List<XWPFParagraph> paragraphs = doc.getParagraphs();
        for (int i = 0; i < paragraphs.size(); i++) {
            XWPFParagraph p = paragraphs.get(i);
            if (shouldSkipFormattingCheck(p, i > 0 ? paragraphs.get(i - 1) : null)) continue;
            for (XWPFRun r : p.getRuns()) {
                Double sz = r.getFontSizeAsDouble();
                if (sz != null && sz > 0 && Math.abs(sz - expectedPt) >= 2) wrongRuns.add(r);
            }
        }
        boolean ok = wrongRuns.isEmpty();
        if (!ok) toHighlight.addAll(wrongRuns);
        Set<Double> sizes = new HashSet<>();
        for (int i = 0; i < paragraphs.size(); i++) {
            if (shouldSkipFormattingCheck(paragraphs.get(i), i > 0 ? paragraphs.get(i - 1) : null)) continue;
            XWPFParagraph p = paragraphs.get(i);
            for (XWPFRun r : p.getRuns()) {
                Double sz = r.getFontSizeAsDouble();
                if (sz != null && sz > 0) sizes.add(sz);
            }
        }
        double actual = sizes.isEmpty() ? 0 : sizes.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        return CriteriaResultDto.builder()
                .code("FONT_SIZE").title("Размер шрифта").passed(ok)
                .message(ok ? "Размер соответствует" : "Обнаружен размер шрифта, отличный от " + expectedSize)
                .expected(expectedSize).actual(sizes.isEmpty() ? "не определён" : String.format("%.0f пт", actual))
                .level(ok ? null : "warning").build();
    }

    private CriteriaResultDto checkLineSpacingDocx(XWPFDocument doc, String expectedSpacing, List<XWPFRun> toHighlight) {
        if (expectedSpacing == null || expectedSpacing.isBlank()) return passed("LINE_SPACING", "Межстрочный интервал", "Проверка не задана");
        double expected = parseLineSpacing(expectedSpacing);
        if (expected <= 0) return passed("LINE_SPACING", "Межстрочный интервал", "Критерий не распознан");
        List<Double> spacings = new ArrayList<>();
        List<XWPFRun> badParagraphFirstRuns = new ArrayList<>();
        List<XWPFParagraph> paragraphs = doc.getParagraphs();
        for (int i = 0; i < paragraphs.size(); i++) {
            XWPFParagraph p = paragraphs.get(i);
            if (shouldSkipFormattingCheck(p, i > 0 ? paragraphs.get(i - 1) : null)) continue;
            double spacingVal = p.getSpacingBetween();
            if (spacingVal > 0 && spacingVal < 2400) {
                double lines = spacingVal / 240.0;
                if (lines > 0 && lines < 10) {
                    spacings.add(lines);
                    if (Math.abs(lines - expected) >= 0.3 && !p.getRuns().isEmpty()) {
                        badParagraphFirstRuns.add(p.getRuns().get(0));
                    }
                }
            }
        }
        boolean ok = spacings.isEmpty() || spacings.stream().anyMatch(s -> Math.abs(s - expected) < 0.3);
        if (!ok && !badParagraphFirstRuns.isEmpty()) toHighlight.addAll(badParagraphFirstRuns);
        double actual = spacings.isEmpty() ? 0 : spacings.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        return CriteriaResultDto.builder()
                .code("LINE_SPACING").title("Межстрочный интервал").passed(ok)
                .message(ok ? "Интервал соответствует" : "Межстрочный интервал отличается от " + expectedSpacing)
                .expected(expectedSpacing).actual(spacings.isEmpty() ? "не определён" : String.format("%.1f", actual))
                .level(ok ? null : "warning").build();
    }

    /** Извлечённая информация о шрифтах и размерах из PDF */
    private record PdfFontInfo(Set<String> fontNames, Set<Float> fontSizesPt) {}

    private boolean shouldSkipForFontCheck(String text) {
        if (text == null || text.isBlank()) return false;
        String t = text.trim();
        if (FIGURE_CAPTION.matcher(t).find() || TABLE_CAPTION.matcher(t).find()) return true;
        if (SECTION_HEADING.matcher(t).find()) return true;
        if (t.length() < 150 && NUMBERED_HEADING.matcher(t).find()) return true;
        return false;
    }

    private PdfFontInfo extractPdfFontInfo(PDDocument doc) throws IOException {
        var fontNames = new HashSet<String>();
        var fontSizesPt = new HashSet<Float>();
        var stripper = new PDFTextStripper() {
            @Override
            protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
                if (shouldSkipForFontCheck(text)) {
                    super.writeString(text, textPositions);
                    return;
                }
                for (TextPosition tp : textPositions) {
                    try {
                        PDFont font = tp.getFont();
                        if (font != null) {
                            String name = font.getName();
                            if (name != null && !name.isBlank()) fontNames.add(name.trim());
                        }
                        float size = tp.getFontSizeInPt();
                        if (size > 0 && size < 100) fontSizesPt.add(size);
                    } catch (Exception e) {
                        log.trace("PDF font extract: {}", e.getMessage());
                    }
                }
                super.writeString(text, textPositions);
            }
        };
        stripper.setSortByPosition(true);
        stripper.getText(doc);
        return new PdfFontInfo(fontNames, fontSizesPt);
    }

    private CriteriaResultDto checkFontPdf(PdfFontInfo info, String expectedFont) {
        if (expectedFont == null || expectedFont.isBlank()) return passed("FONT", "Шрифт", "Проверка не задана");
        if (info == null) return CriteriaResultDto.builder().code("FONT").title("Шрифт").passed(true)
                .message("Шрифты не извлечены").expected(expectedFont).actual("—").level(null).build();
        try {
            if (info.fontNames().isEmpty()) {
                return CriteriaResultDto.builder().code("FONT").title("Шрифт").passed(true)
                        .message("Шрифты в PDF не определены")
                        .expected(expectedFont).actual("—").level(null).build();
            }
            String normalizedExpected = normalizeFont(expectedFont);
            boolean ok = info.fontNames().stream()
                    .anyMatch(f -> normalizeFont(f).contains(normalizedExpected) || normalizedExpected.contains(normalizeFont(f)));
            return CriteriaResultDto.builder().code("FONT").title("Шрифт").passed(ok)
                    .message(ok ? "Шрифт соответствует" : "Обнаружен шрифт, отличный от " + expectedFont)
                    .expected(expectedFont).actual(String.join(", ", info.fontNames()))
                    .level(ok ? null : "warning").build();
        } catch (Exception e) {
            log.warn("PDF font check failed: {}", e.getMessage());
            return CriteriaResultDto.builder().code("FONT").title("Шрифт").passed(true)
                    .message("Ошибка: " + e.getMessage()).expected(expectedFont).actual("—").level(null).build();
        }
    }

    private CriteriaResultDto checkFontSizePdf(PdfFontInfo info, String expectedSize) {
        if (expectedSize == null || expectedSize.isBlank()) return passed("FONT_SIZE", "Размер шрифта", "Проверка не задана");
        double expectedPt = parseFontSize(expectedSize);
        if (expectedPt <= 0) return passed("FONT_SIZE", "Размер шрифта", "Критерий не распознан");
        if (info == null) return CriteriaResultDto.builder().code("FONT_SIZE").title("Размер шрифта").passed(true)
                .message("Размеры не извлечены").expected(expectedSize).actual("—").level(null).build();
        try {
            if (info.fontSizesPt().isEmpty()) {
                return CriteriaResultDto.builder().code("FONT_SIZE").title("Размер шрифта").passed(true)
                        .message("Размеры шрифтов в PDF не определены")
                        .expected(expectedSize).actual("—").level(null).build();
            }
            boolean ok = info.fontSizesPt().stream()
                    .anyMatch(s -> Math.abs(s - expectedPt) < 2);
            double avg = info.fontSizesPt().stream().mapToDouble(Float::doubleValue).average().orElse(0);
            return CriteriaResultDto.builder().code("FONT_SIZE").title("Размер шрифта").passed(ok)
                    .message(ok ? "Размер соответствует" : "Обнаружен размер, отличный от " + expectedSize)
                    .expected(expectedSize).actual(String.format("%.0f пт", avg))
                    .level(ok ? null : "warning").build();
        } catch (Exception e) {
            log.warn("PDF font size check failed: {}", e.getMessage());
            return CriteriaResultDto.builder().code("FONT_SIZE").title("Размер шрифта").passed(true)
                    .message("Ошибка: " + e.getMessage()).expected(expectedSize).actual("—").level(null).build();
        }
    }

    private CriteriaResultDto checkLineSpacingPdf(PDDocument doc, String expectedSpacing) {
        return CriteriaResultDto.builder().code("LINE_SPACING").title("Межстрочный интервал").passed(true)
                .message("Проверка интервала в PDF недоступна (анализ позиций текста)")
                .expected(expectedSpacing).actual("—").level(null).build();
    }

    private CriteriaResultDto checkMinPages(int actual, int minRequired) {
        boolean ok = actual >= minRequired;
        return CriteriaResultDto.builder()
                .code("MIN_PAGES")
                .title("Минимальное количество страниц")
                .passed(ok)
                .message(ok ? "Объём достаточный" : "Недостаточно страниц: " + actual + " из " + minRequired)
                .expected(String.valueOf(minRequired))
                .actual(String.valueOf(actual))
                .level(ok ? null : "error")
                .build();
    }

    private CriteriaResultDto checkMinSources(int actual, int minRequired) {
        boolean ok = actual >= minRequired;
        return CriteriaResultDto.builder()
                .code("MIN_SOURCES")
                .title("Минимальное количество источников")
                .passed(ok)
                .message(ok ? "Источников достаточно" : "Недостаточно источников: " + actual + " из " + minRequired)
                .expected(String.valueOf(minRequired))
                .actual(String.valueOf(actual))
                .level(ok ? null : "warning")
                .build();
    }

    private static final int FIRST_PAGE_CHARS = 3500;

    private List<CriteriaResultDto> checkStructureDocx(XWPFDocument doc, TemplateCriteriaDto t) {
        StringBuilder fullText = new StringBuilder();
        for (var elem : doc.getBodyElements()) {
            if (elem instanceof XWPFParagraph p) {
                String pt = p.getText();
                if (pt != null) fullText.append(pt).append("\n");
            } else if (elem instanceof XWPFTable table) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        for (XWPFParagraph cellP : cell.getParagraphs()) {
                            String pt = cellP.getText();
                            if (pt != null) fullText.append(pt).append("\n");
                        }
                    }
                }
            }
        }
        String full = fullText.toString();
        String firstPage = full.length() > FIRST_PAGE_CHARS ? full.substring(0, FIRST_PAGE_CHARS) : full;
        List<CriteriaResultDto> list = checkStructure(full, firstPage, t);
        list.addAll(checkIllustrationsAndTablesDocx(doc, t));
        return list;
    }

    /**
     * Проверки по шаблону: нумерация иллюстраций (сквозная), позиция рисунка (по центру),
     * подпись под рисунком, заголовок таблицы («Таблица» + номер). Ищет триггеры в тексте и проверяет расположение.
     */
    private List<CriteriaResultDto> checkIllustrationsAndTablesDocx(XWPFDocument doc, TemplateCriteriaDto t) {
        List<CriteriaResultDto> results = new ArrayList<>();
        String illNum = t.getIllNumbering();
        String figPos = t.getFigurePosition();
        String figCap = t.getFigureCaption();
        String tblTitle = t.getTableTitle();
        if ((illNum == null || illNum.isBlank()) && (figPos == null || figPos.isBlank())
                && (figCap == null || figCap.isBlank()) && (tblTitle == null || tblTitle.isBlank())) {
            return results;
        }

        List<Integer> figureNumbers = new ArrayList<>();
        List<Integer> tableNumbers = new ArrayList<>();
        List<Boolean> figureCaptionCentered = new ArrayList<>();

        for (var elem : doc.getBodyElements()) {
            if (elem instanceof XWPFParagraph p) {
                String line = p.getText();
                if (line != null && !line.isBlank()) {
                    String trimmed = line.trim();
                    if (FIGURE_CAPTION.matcher(trimmed).find()) {
                        extractNumbers(FIGURE_NUMBER, trimmed, figureNumbers);
                        boolean centered = isParagraphCentered(p);
                        figureCaptionCentered.add(centered);
                    }
                    if (TABLE_CAPTION.matcher(trimmed).find()) {
                        extractNumbers(TABLE_NUMBER, trimmed, tableNumbers);
                    }
                }
            } else if (elem instanceof XWPFTable table) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        for (XWPFParagraph cellP : cell.getParagraphs()) {
                            String line = cellP.getText();
                            if (line != null && !line.isBlank()) {
                                String trimmed = line.trim();
                                if (FIGURE_CAPTION.matcher(trimmed).find()) {
                                    extractNumbers(FIGURE_NUMBER, trimmed, figureNumbers);
                                    figureCaptionCentered.add(isParagraphCentered(cellP));
                                }
                                if (TABLE_CAPTION.matcher(trimmed).find()) {
                                    extractNumbers(TABLE_NUMBER, trimmed, tableNumbers);
                                }
                            }
                        }
                    }
                }
            }
        }

        if (illNum != null && (illNum.contains("Сквозная") || illNum.contains("сквозная"))) {
            boolean ok = isContinuousNumbering(figureNumbers);
            results.add(CriteriaResultDto.builder()
                    .code("ILL_NUMBERING")
                    .title("Нумерация иллюстраций")
                    .passed(ok)
                    .message(ok ? "Нумерация сквозная (1, 2, 3, …)" : formatNumberingMessage("Рис.", figureNumbers))
                    .expected("Сквозная")
                    .actual(ok ? "Сквозная" : formatNumberingMessage("Рис.", figureNumbers))
                    .level(ok ? null : "warning")
                    .build());
        }
        if (figPos != null && (figPos.contains("центру") || figPos.contains("центр"))) {
            boolean allCentered = figureCaptionCentered.isEmpty() || figureCaptionCentered.stream().allMatch(Boolean::booleanValue);
            results.add(CriteriaResultDto.builder()
                    .code("FIGURE_POSITION")
                    .title("Позиция рисунка")
                    .passed(allCentered)
                    .message(allCentered ? "Подписи рисунков по центру страницы" : "Не все подписи рисунков по центру")
                    .expected("По центру страницы")
                    .actual(allCentered ? "По центру страницы" : "Проверьте выравнивание подписей")
                    .level(allCentered ? null : "warning")
                    .build());
        }
        if (figCap != null && (figCap.contains("Под рисунком") || figCap.contains("под рисунком"))) {
            boolean hasCaptions = !figureNumbers.isEmpty();
            results.add(CriteriaResultDto.builder()
                    .code("FIGURE_CAPTION_POS")
                    .title("Подпись рисунка")
                    .passed(hasCaptions)
                    .message(hasCaptions ? "Найдены подписи вида «Рис. N» (под рисунком — визуально)" : "Подписи рисунков не найдены")
                    .expected("Под рисунком")
                    .actual(hasCaptions ? "Под рисунком" : "Не найдено")
                    .level(hasCaptions ? null : "warning")
                    .build());
        }
        if (tblTitle != null && !tblTitle.isBlank()) {
            boolean tableFormatOk = isContinuousNumbering(tableNumbers);
            results.add(CriteriaResultDto.builder()
                    .code("TABLE_TITLE")
                    .title("Заголовок таблицы")
                    .passed(tableFormatOk)
                    .message(tableFormatOk ? "Заголовки таблиц: «Таблица» + номер" : formatNumberingMessage("Таблица", tableNumbers))
                    .expected("Слово «Таблица» + номер")
                    .actual(tableFormatOk ? "OK" : formatNumberingMessage("Таблица", tableNumbers))
                    .level(tableFormatOk ? null : "warning")
                    .build());
        }
        return results;
    }

    private boolean isParagraphCentered(XWPFParagraph p) {
        if (p == null) return false;
        ParagraphAlignment a = p.getAlignment();
        return a == ParagraphAlignment.CENTER;
    }

    private void extractNumbers(Pattern pattern, String line, List<Integer> out) {
        java.util.regex.Matcher m = pattern.matcher(line);
        while (m.find()) {
            try {
                out.add(Integer.parseInt(m.group(1)));
            } catch (NumberFormatException ignored) { }
        }
    }

    private boolean isContinuousNumbering(List<Integer> numbers) {
        if (numbers.isEmpty()) return true;
        List<Integer> sorted = new ArrayList<>(new LinkedHashSet<>(numbers));
        Collections.sort(sorted);
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i) != i + 1) return false;
        }
        return true;
    }

    private String formatNumberingMessage(String prefix, List<Integer> numbers) {
        if (numbers.isEmpty()) return "Не найдено";
        List<Integer> sorted = new ArrayList<>(new LinkedHashSet<>(numbers));
        Collections.sort(sorted);
        return prefix + ": " + sorted.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }

    private List<CriteriaResultDto> checkStructurePdf(PDDocument doc, TemplateCriteriaDto t) throws Exception {
        PDFTextStripper fullStripper = new PDFTextStripper();
        fullStripper.setSortByPosition(true);
        fullStripper.setAddMoreFormatting(true);
        String fullText = fullStripper.getText(doc);
        String firstPageText = fullText;
        if (doc.getNumberOfPages() >= 1) {
            PDFTextStripper page1Stripper = new PDFTextStripper();
            page1Stripper.setSortByPosition(true);
            page1Stripper.setAddMoreFormatting(true);
            page1Stripper.setStartPage(1);
            page1Stripper.setEndPage(1);
            firstPageText = page1Stripper.getText(doc);
        }
        if (firstPageText.isEmpty()) firstPageText = fullText.length() > 2500 ? fullText.substring(0, 2500) : fullText;
        List<CriteriaResultDto> list = checkStructure(fullText, firstPageText, t);
        list.addAll(checkIllustrationsAndTablesFromText(fullText, t));
        return list;
    }

    private List<CriteriaResultDto> checkStructure(String fullText, String firstPageText, TemplateCriteriaDto t) {
        List<CriteriaResultDto> list = new ArrayList<>();
        String lower = fullText.toLowerCase();
        if (Boolean.TRUE.equals(t.getHasTitlePage()) || (t.getTitlePageRequiredStrings() != null && !t.getTitlePageRequiredStrings().isEmpty())) {
            CriteriaResultDto titleResult = checkTitlePageByRequiredStrings(firstPageText, t.getTitlePageRequiredStrings());
            if (titleResult != null) list.add(titleResult);
        }
        if (Boolean.TRUE.equals(t.getHasToc())) {
            boolean found = lower.contains("содержание") || lower.contains("оглавление");
            list.add(result("HAS_TOC", "Оглавление", found, "Оглавление", found ? "Найдено" : "Не найдено"));
        }
        if (Boolean.TRUE.equals(t.getHasIntroduction())) {
            boolean found = lower.contains("введение");
            list.add(result("HAS_INTRODUCTION", "Введение", found, "Введение", found ? "Найдено" : "Не найдено"));
        }
        if (Boolean.TRUE.equals(t.getHasMainPart())) {
            list.add(passed("HAS_MAIN_PART", "Основная часть", "Проверка осуществляется визуально"));
        }
        if (Boolean.TRUE.equals(t.getHasConclusion())) {
            boolean found = lower.contains("заключение");
            list.add(result("HAS_CONCLUSION", "Заключение", found, "Заключение", found ? "Найдено" : "Не найдено"));
        }
        if (Boolean.TRUE.equals(t.getHasBibliography())) {
            boolean found = hasBibliographySection(lower);
            list.add(result("HAS_BIBLIOGRAPHY", "Список источников/литературы", found, "СПИСОК ИСПОЛЬЗОВАННЫХ ИСТОЧНИКОВ / Список литературы", found ? "Найдено" : "Не найдено"));
        }
        if (Boolean.TRUE.equals(t.getHasAppendices())) {
            boolean found = lower.contains("приложени");
            list.add(result("HAS_APPENDICES", "Приложения", found, "Приложения", found ? "Найдено" : "Не найдено"));
        }
        return list;
    }

    /** Проверки иллюстраций и таблиц по полному тексту (PDF или когда нет доступа к разметке). */
    private List<CriteriaResultDto> checkIllustrationsAndTablesFromText(String fullText, TemplateCriteriaDto t) {
        List<CriteriaResultDto> results = new ArrayList<>();
        String illNum = t.getIllNumbering();
        String figCap = t.getFigureCaption();
        String tblTitle = t.getTableTitle();
        if ((illNum == null || illNum.isBlank()) && (figCap == null || figCap.isBlank()) && (tblTitle == null || tblTitle.isBlank())) {
            return results;
        }
        List<Integer> figureNumbers = new ArrayList<>();
        List<Integer> tableNumbers = new ArrayList<>();
        for (String line : fullText.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (FIGURE_CAPTION.matcher(trimmed).find()) extractNumbers(FIGURE_NUMBER, trimmed, figureNumbers);
            if (TABLE_CAPTION.matcher(trimmed).find()) extractNumbers(TABLE_NUMBER, trimmed, tableNumbers);
        }
        if (illNum != null && (illNum.contains("Сквозная") || illNum.contains("сквозная"))) {
            boolean ok = isContinuousNumbering(figureNumbers);
            results.add(CriteriaResultDto.builder()
                    .code("ILL_NUMBERING")
                    .title("Нумерация иллюстраций")
                    .passed(ok)
                    .message(ok ? "Нумерация сквозная (1, 2, 3, …)" : formatNumberingMessage("Рис.", figureNumbers))
                    .expected("Сквозная")
                    .actual(ok ? "Сквозная" : formatNumberingMessage("Рис.", figureNumbers))
                    .level(ok ? null : "warning")
                    .build());
        }
        if (figCap != null && (figCap.contains("Под рисунком") || figCap.contains("под рисунком"))) {
            boolean hasCaptions = !figureNumbers.isEmpty();
            results.add(CriteriaResultDto.builder()
                    .code("FIGURE_CAPTION_POS")
                    .title("Подпись рисунка")
                    .passed(hasCaptions)
                    .message(hasCaptions ? "Найдены подписи вида «Рис. N»" : "Подписи рисунков не найдены")
                    .expected("Под рисунком")
                    .actual(hasCaptions ? "Под рисунком" : "Не найдено")
                    .level(hasCaptions ? null : "warning")
                    .build());
        }
        if (tblTitle != null && !tblTitle.isBlank()) {
            boolean tableFormatOk = isContinuousNumbering(tableNumbers);
            results.add(CriteriaResultDto.builder()
                    .code("TABLE_TITLE")
                    .title("Заголовок таблицы")
                    .passed(tableFormatOk)
                    .message(tableFormatOk ? "Заголовки таблиц: «Таблица» + номер" : formatNumberingMessage("Таблица", tableNumbers))
                    .expected("Слово «Таблица» + номер")
                    .actual(tableFormatOk ? "OK" : formatNumberingMessage("Таблица", tableNumbers))
                    .level(tableFormatOk ? null : "warning")
                    .build());
        }
        return results;
    }

    /** Проверка титульного листа по обязательным строкам (первая страница / начало документа). */
    private CriteriaResultDto checkTitlePageByRequiredStrings(String firstPageText, List<String> requiredStrings) {
        if (requiredStrings == null || requiredStrings.isEmpty()) {
            boolean hasTitleLike = firstPageText.toLowerCase().contains("министерство")
                    || firstPageText.toLowerCase().contains("диплом")
                    || firstPageText.toLowerCase().contains("курсов");
            return result("HAS_TITLE_PAGE", "Титульный лист", hasTitleLike, "Титульный лист", hasTitleLike ? "Найдено" : "Проверьте наличие");
        }
        String lower = firstPageText.toLowerCase();
        List<String> missing = new ArrayList<>();
        for (String s : requiredStrings) {
            if (s == null || s.isBlank()) continue;
            if (!lower.contains(s.toLowerCase().trim())) missing.add(s);
        }
        boolean ok = missing.isEmpty();
        return CriteriaResultDto.builder()
                .code("HAS_TITLE_PAGE").title("Титульный лист по шаблону").passed(ok)
                .message(ok ? "Все обязательные элементы найдены" : "Не найдено: " + String.join(", ", missing))
                .expected(String.join("; ", requiredStrings))
                .actual(ok ? "OK" : "отсутствуют: " + String.join(", ", missing))
                .level(ok ? null : "warning").build();
    }

    private int countPagesDocx(XWPFDocument doc) {
        int paragraphs = doc.getParagraphs().size();
        int tables = doc.getTables().stream().mapToInt(t -> t.getRows().size()).sum();
        int totalBlocks = paragraphs + tables;
        return Math.max(1, (int) Math.ceil(totalBlocks / 15.0));
    }

    private int countSourcesDocx(XWPFDocument doc) {
        StringBuilder sb = new StringBuilder();
        for (XWPFParagraph p : doc.getParagraphs()) {
            sb.append(p.getText()).append("\n");
        }
        for (XWPFTable t : doc.getTables()) {
            for (XWPFTableRow row : t.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph p : cell.getParagraphs()) {
                        sb.append(p.getText()).append("\n");
                    }
                }
            }
        }
        return countBibliographyEntries(sb.toString());
    }

    private int countSourcesPdf(PDDocument doc) throws Exception {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);  // предсказуемый порядок при колонках
        stripper.setAddMoreFormatting(true);  // пробелы между словами
        return countBibliographyEntries(stripper.getText(doc));
    }

    /** Проверка наличия раздела «Список источников» / «Список литературы» */
    private boolean hasBibliographySection(String textLower) {
        return textLower.contains("список использованных источников")
                || textLower.contains("список литературы")
                || textLower.contains("библиограф")
                || textLower.contains("использованные источники")
                || (textLower.contains("источники") && textLower.contains("список"));
    }

    /**
     * Подсчёт записей в списке источников.
     * Приоритет: [Электронный ресурс] в разделе → структурированный подсчёт → макс. номер в разделе.
     */
    private int countBibliographyEntries(String text) {
        String textLower = text.toLowerCase();
        int sectionStart = findBibliographySectionStart(textLower);
        String searchArea = sectionStart >= 0 ? text.substring(sectionStart) : text;

        log.debug("Bibliography section: found={}, searchArea length={}", sectionStart >= 0, searchArea.length());

        // 1. ГОСТ-стиль — ищем в зоне раздела источников, чтобы не ловить "1. Введение" и т.п.
        String areaToSearch = searchArea.length() > 0 ? searchArea : text;
        var gostMatcher = BIBLIOGRAPHY_PATTERN.matcher(areaToSearch);
        int gostCount = 0;
        while (gostMatcher.find()) gostCount++;
        if (gostCount > 0) {
            logSourcesWithCount("GOST", gostCount, extractSourcePreviews(areaToSearch));
            return gostCount;
        }

        // 2. "электронный ресурс" (гибко: с любыми скобками) — самый надёжный признак
        if (textLower.contains("электронный ресурс")) {
            int electronicCount = countOccurrences(searchArea, "электронный ресурс", true);
            if (electronicCount > 0 && electronicCount <= 100) {
                List<String> previews = extractSourcePreviews(searchArea);
                logSources("электронный ресурс (count=" + electronicCount + ")", previews.isEmpty() ? List.of("(нумерация не распознана)") : previews);
                return electronicCount;
            }
        }

        // 3. Структурированный подсчёт: нумерация + маркер/URL/год
        var structured = countStructuredSourcesWithPreview(searchArea);
        if (structured.count > 0) {
            logSources("структурированный", structured.previews);
            return structured.count;
        }

        // 4. Максимальный номер в разделе
        if (sectionStart >= 0) {
            int maxNum = maxNumberedEntry(searchArea);
            if (maxNum > 0) {
                logSources("maxNum=" + maxNum, extractSourcePreviews(searchArea));
                return maxNum;
            }
        }

        // 5. По всему документу, если раздела не нашли
        if (textLower.contains("url") || textLower.contains("http")) {
            int maxNum = maxNumberedEntry(text);
            if (maxNum > 0 && maxNum <= 50) return maxNum;
        }

        log.warn("Bibliography: no sources found. searchArea preview: {}...", searchArea.length() > 300 ? searchArea.substring(0, 300) : searchArea);
        return 0;
    }

    private void logSources(String method, List<String> previews) {
        log.info("=== Найдено источников (метод: {}): {} ===", method, previews.size());
        for (int i = 0; i < previews.size(); i++) {
            String p = previews.get(i);
            if (p.length() > 100) p = p.substring(0, 97) + "...";
            log.info("  {}. {}", i + 1, p.replace("\n", " "));
        }
    }

    private void logSourcesWithCount(String method, int count, List<String> previews) {
        log.info("=== Найдено источников (метод: {}): {} ===", method, count);
        for (int i = 0; i < Math.min(previews.size(), 10); i++) {
            String p = previews.get(i);
            if (p.length() > 100) p = p.substring(0, 97) + "...";
            log.info("  {}. {}", i + 1, p.replace("\n", " "));
        }
        if (previews.size() > 10) log.info("  ... и ещё {}", previews.size() - 10);
    }

    private List<String> extractSourcePreviews(String text) {
        var m = NUMBERED_ENTRY_PATTERN.matcher(text);
        List<int[]> ranges = new ArrayList<>();
        while (m.find()) {
            try {
                int n = Integer.parseInt(m.group(1));
                if (n >= 1 && n <= 200) ranges.add(new int[]{m.start(), m.end()});
            } catch (NumberFormatException ignored) {}
        }
        List<String> result = new ArrayList<>();
        for (int i = 0; i < ranges.size(); i++) {
            int end = (i + 1 < ranges.size()) ? ranges.get(i + 1)[0] : text.length();
            String block = text.substring(ranges.get(i)[0], Math.min(end, ranges.get(i)[0] + 200)).trim();
            result.add(block);
        }
        return result;
    }

    private record StructuredResult(int count, List<String> previews) {}

    /**
     * Подсчёт по шаблону: записи вида "N. название [Электронный ресурс] URL: ..."
     */
    private StructuredResult countStructuredSourcesWithPreview(String text) {
        var m = NUMBERED_ENTRY_PATTERN.matcher(text);
        List<int[]> ranges = new ArrayList<>();
        while (m.find()) {
            try {
                int n = Integer.parseInt(m.group(1));
                if (n >= 1 && n <= 200) ranges.add(new int[]{m.start(), m.end(), n});
            } catch (NumberFormatException ignored) {}
        }
        if (ranges.isEmpty()) return new StructuredResult(0, List.of());

        int validCount = 0;
        List<String> previews = new ArrayList<>();
        for (int i = 0; i < ranges.size(); i++) {
            int blockStart = ranges.get(i)[0];
            int blockEnd = (i + 1 < ranges.size()) ? ranges.get(i + 1)[0] : text.length();
            String block = text.substring(blockStart, blockEnd);
            String blockLower = block.toLowerCase();
            boolean hasMarker = blockLower.contains("электронный ресурс");
            boolean hasUrl = block.contains("URL") || block.contains("url") || block.contains("http");
            boolean hasYear = block.matches(".*\\d{4}.*");
            if (hasMarker || hasUrl || hasYear) {
                validCount++;
                previews.add(block.replace("\n", " ").trim());
            }
        }
        return new StructuredResult(validCount > 0 ? validCount : 0, previews);
    }

    private int countOccurrences(String text, String substr, boolean ignoreCase) {
        if (text == null || substr == null || substr.isEmpty()) return 0;
        String s = ignoreCase ? text.toLowerCase() : text;
        String sub = ignoreCase ? substr.toLowerCase() : substr;
        int count = 0, idx = 0;
        while ((idx = s.indexOf(sub, idx)) >= 0) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    private int maxNumberedEntry(String text) {
        var m = NUMBERED_ENTRY_PATTERN.matcher(text);
        int max = 0;
        while (m.find()) {
            try {
                int n = Integer.parseInt(m.group(1));
                if (n > max && n <= 200) max = n;
            } catch (NumberFormatException ignored) {}
        }
        return max;
    }

    /** Индекс начала раздела «Список источников». Берём последнее вхождение — раздел обычно в конце. */
    private int findBibliographySectionStart(String textLower) {
        String[] markers = {
                "список использованных источников",
                "список литературы",
                "список источников",
                "библиограф",
                "использованные источники"
        };
        int pos = -1;
        for (String m : markers) {
            int i = textLower.lastIndexOf(m);  // последнее вхождение — раздел ближе к концу
            if (i >= 0 && (pos < 0 || i > pos)) pos = i;
        }
        return pos;
    }

    private ValidationResultDto buildResult(List<CriteriaResultDto> results) {
        List<CriteriaResultDto> errors = results.stream()
                .filter(r -> !r.isPassed() && "error".equals(r.getLevel()))
                .collect(Collectors.toList());
        List<CriteriaResultDto> warnings = results.stream()
                .filter(r -> !r.isPassed() && ("warning".equals(r.getLevel()) || r.getLevel() == null))
                .collect(Collectors.toList());
        boolean valid = errors.isEmpty();
        int total = results.size();
        int passedCount = (int) results.stream().filter(CriteriaResultDto::isPassed).count();
        int percent = total > 0 ? Math.round(100f * passedCount / total) : 100;
        return ValidationResultDto.builder()
                .valid(valid)
                .percent(percent)
                .criteria(results)
                .errors(errors)
                .warnings(warnings)
                .build();
    }

    private CriteriaResultDto passed(String code, String title, String message) {
        return CriteriaResultDto.builder()
                .code(code)
                .title(title)
                .passed(true)
                .message(message)
                .build();
    }

    private CriteriaResultDto result(String code, String title, boolean ok, String expected, String actual) {
        return CriteriaResultDto.builder()
                .code(code)
                .title(title)
                .passed(ok)
                .message(ok ? "Соответствует" : "Не найдено")
                .expected(expected)
                .actual(actual)
                .level(ok ? null : "warning")
                .build();
    }

    private String getExtension(String filename) {
        if (filename == null) return "";
        int i = filename.lastIndexOf('.');
        return i >= 0 ? filename.substring(i) : "";
    }

    private int parseInt(String s, int fallback) {
        if (s == null || s.isBlank()) return fallback;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private double parseFontSize(String s) {
        if (s == null || s.isBlank()) return 0;
        try {
            return Double.parseDouble(s.replaceAll("[^0-9.,]", "").replace(",", "."));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private double parseLineSpacing(String s) {
        if (s == null || s.isBlank()) return 0;
        try {
            return Double.parseDouble(s.replaceAll("[^0-9.,]", "").replace(",", "."));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String normalizeFont(String font) {
        if (font == null) return "";
        return font.toLowerCase()
                .replace(" ", "")
                .replaceAll("[-_]bold[-_]?$", "")
                .replaceAll("[-_]italic[-_]?$", "")
                .replaceAll("psmt$", "")
                .replaceAll("std$", "")
                .replaceAll("-?bolditalic$", "")
                .trim();
    }

    private TemplateCriteriaDto defaultTemplate() {
        return TemplateCriteriaDto.builder()
                .fileFormat(".doc или .docx")
                .font("Times New Roman")
                .fontSize("14 пт")
                .lineSpacing("1.5")
                .minPages("10")
                .minSources("7")
                .hasTitlePage(true)
                .titlePageRequiredStrings(List.of("МИНИСТЕРСТВО", "Санкт-Петербург"))
                .hasToc(true)
                .hasIntroduction(true)
                .hasConclusion(true)
                .hasBibliography(true)
                .build();
    }
}
