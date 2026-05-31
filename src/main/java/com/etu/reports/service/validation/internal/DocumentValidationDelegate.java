package com.etu.reports.service.validation.internal;

import com.etu.reports.dto.template.TemplateCriteriaDto;
import com.etu.reports.dto.validation.CriteriaResultDto;
import com.etu.reports.dto.validation.ValidationResultDto;
import com.etu.reports.service.TitlePageTemplateService;
import com.etu.reports.service.validation.annotation.DocxCommentAnnotator;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPrGeneral;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.Base64;

/**
 * Вся низкоуровневая логика проверки DOCX/PDF (вынесена из монолитного сервиса).
 * Вызывается из {@link com.etu.reports.service.validation.ValidationFacade} и правил.
 */
@Component
public class DocumentValidationDelegate {

    private static final Logger log = LoggerFactory.getLogger(DocumentValidationDelegate.class);

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
    /** Допустимое отклонение размера шрифта от шаблона, пт (меньше — считаем тем же кеглем). */
    private static final double FONT_SIZE_TOLERANCE_PT = 2.0;

    private final TitlePageTemplateService titlePageTemplateService;
    private final DocxCommentAnnotator docxCommentAnnotator;

    public DocumentValidationDelegate(TitlePageTemplateService titlePageTemplateService,
                                    DocxCommentAnnotator docxCommentAnnotator) {
        this.titlePageTemplateService = titlePageTemplateService;
        this.docxCommentAnnotator = docxCommentAnnotator;
    }

    /**
     * Валидирует документ по критериям шаблона.
     *
     * @param filename  исходное имя файла
     * @param content   содержимое файла
     * @param template  критерии шаблона
     * @param annotate  если true — вернёт файл с выделенными ошибками
     */
    public ValidationResultDto validate(String filename, byte[] content, TemplateCriteriaDto template, boolean annotate) {
        if (template == null) {
            template = defaultTemplate();
        } else {
            template = ensureIllustrationTableCriteria(template);
        }

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

    /**
     * Собирает все критерии проверки DOCX (используется {@link com.etu.reports.service.validation.ValidationRuleEngine}).
     */
    public List<CriteriaResultDto> collectDocxValidation(XWPFDocument doc, TemplateCriteriaDto template, List<XWPFRun> runsToHighlight) {
        List<CriteriaResultDto> results = new ArrayList<>();
        results.add(criteriaFileFormat(true, template.getFileFormat()));
        results.add(checkFontDocx(doc, template.getFont(), runsToHighlight));
        results.add(checkFontSizeDocx(doc, template.getFontSize(), runsToHighlight));
        int pageCount = countPagesDocx(doc);
        results.add(checkMinPages(pageCount, parseInt(template.getMinPages(), 5)));
        int sourcesCount = countSourcesDocx(doc);
        results.add(checkMinSources(sourcesCount, parseInt(template.getMinSources(), 5)));
        if (shouldCheckTitlePage(template)) {
            results.add(checkTitlePageByRequiredStrings(
                    titlePageTemplateService.extractFirstPageTextFromDocx(doc),
                    template.getTitlePageRequiredStrings()));
        }
        results.addAll(checkStructureDocx(doc, template, runsToHighlight));
        return results;
    }

    public ValidationResultDto validateDocx(byte[] content, TemplateCriteriaDto template, String filename, boolean annotate) throws Exception {
        List<CriteriaResultDto> results;
        List<XWPFRun> runsToHighlight = new ArrayList<>();

        try (InputStream is = new java.io.ByteArrayInputStream(content);
             XWPFDocument doc = new XWPFDocument(is)) {

            results = new ArrayList<>(collectDocxValidation(doc, template, runsToHighlight));
            boolean anyFailed = results.stream().anyMatch(r -> !r.isPassed());
            if (annotate && (!runsToHighlight.isEmpty() || anyFailed)) {
                for (XWPFRun r : runsToHighlight) {
                    try { r.setColor(HIGHLIGHT_COLOR); } catch (Exception ignored) {}
                }
                List<CriteriaResultDto> failed = results.stream()
                        .filter(r -> !r.isPassed())
                        .collect(Collectors.toList());
                docxCommentAnnotator.annotate(doc, failed, runsToHighlight);
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

    public byte[] writeDocx(XWPFDocument doc) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        doc.write(out);
        return out.toByteArray();
    }

    public List<CriteriaResultDto> collectPdfValidation(PDDocument doc, TemplateCriteriaDto template) throws Exception {
        List<CriteriaResultDto> results = new ArrayList<>();
        results.add(criteriaFileFormat(true, template.getFileFormat()));
        PdfFontInfo fontInfo = null;
        try {
            fontInfo = extractPdfFontInfo(doc);
        } catch (Exception ignored) {
        }
        results.add(checkFontPdf(fontInfo, template.getFont()));
        results.add(checkFontSizePdf(fontInfo, template.getFontSize()));
        int pageCount = doc.getNumberOfPages();
        results.add(checkMinPages(pageCount, parseInt(template.getMinPages(), 5)));
        int sourcesCount = countSourcesPdf(doc);
        results.add(checkMinSources(sourcesCount, parseInt(template.getMinSources(), 5)));
        if (shouldCheckTitlePage(template)) {
            PDFTextStripper firstPageStripper = new PDFTextStripper();
            firstPageStripper.setStartPage(1);
            firstPageStripper.setEndPage(1);
            results.add(checkTitlePageByRequiredStrings(
                    firstPageStripper.getText(doc),
                    template.getTitlePageRequiredStrings()));
        }
        results.addAll(checkStructurePdf(doc, template));
        return results;
    }

    public ValidationResultDto validatePdf(byte[] content, TemplateCriteriaDto template, String filename, boolean annotate) throws Exception {
        List<CriteriaResultDto> results = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(content)) {
            results = new ArrayList<>(collectPdfValidation(doc, template));

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

    public byte[] writePdf(PDDocument doc) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        doc.save(out);
        return out.toByteArray();
    }

    public CriteriaResultDto criteriaFileFormat(boolean ok, String expected) {
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
    private static final Pattern HEADING_STYLE = Pattern.compile("(?iu)(heading|заголовок|title|caption|toc|оглавление|заголовок\\s*\\d|heading\\s*\\d)");
    /** Подпись рисунка: «Рис.»/«рис.», «Рисунок», «Figure» + опционально номер. {@code (?iu)} — кириллица без учёта регистра. */
    private static final Pattern FIGURE_CAPTION = Pattern.compile("(?iu)^\\s*(рис\\.?|рисунок|figure|fig\\.)\\s*\\d");
    private static final Pattern FIGURE_CAPTION_LINE = Pattern.compile("(?iu)^\\s*(рис\\.?|рисунок|figure|fig\\.)");
    private static final Pattern TABLE_CAPTION = Pattern.compile("(?iu)^\\s*(таблица|table)\\s*\\d");
    /**
     * Строка заголовка таблицы: «Таблица» / «table» в начале абзаца.
     * Без {@code \\b}: в Java по умолчанию граница слова не работает для кириллицы, из‑за чего «Таблица 1» не находилась.
     */
    private static final Pattern TABLE_TITLE_LINE = Pattern.compile(
            "(?iu)^\\s*(?:\\d+[.)]\\s*)?(таблица|table)(?:\\s*\\d|\\s+[—–\\-]|\\s*$|$)");
    /** Сокращение «Табл 3» / «Табл. 2» — не распознаётся как корректный заголовок. */
    private static final Pattern TABLE_TITLE_ABBREV_TYPO = Pattern.compile(
            "(?iu)^\\s*(?:\\d+[.)]\\s*)?табл\\.?\\s*\\d");
    /** Частая опечатка «таблитца». */
    private static final Pattern TABLE_TITLE_TYPO_TABLITC = Pattern.compile("(?iu)^\\s*(?:\\d+[.)]\\s*)?таблитц");
    /** Извлечение номера из подписи рисунка: "Рис. 1" / "рис 8" -> 1 / 8 */
    private static final Pattern FIGURE_NUMBER = Pattern.compile("(?iu)(?:рис\\.?|рисунок|figure|fig\\.)\\s*(\\d+)");
    /** Извлечение номера из заголовка таблицы: "Таблица 1" -> 1 */
    private static final Pattern TABLE_NUMBER = Pattern.compile("(?iu)(?:таблица|table)\\s*(\\d+)");
    /** Заголовки разделов — только в начале строки (с опциональным номером) */
    private static final Pattern SECTION_HEADING = Pattern.compile("(?iu)^\\s*(\\d+(\\.\\d+)*\\.?\\s*)?(содержание|оглавление|введение|заключение|список\\s+(использованных\\s+)?источников|список\\s+литературы|приложени)\\b");
    /** Нумерованные заголовки: 1. 2.1 3.2.1 и т.д. в начале строки */
    private static final Pattern NUMBERED_HEADING = Pattern.compile("^\\s*\\d+(\\.\\d+)*\\.?\\s*[А-Яа-яA-Z]");

    private static boolean containsDigit(String s) {
        if (s == null) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (Character.isDigit(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /** Первое «слово» строки, по краям только буквы (для сравнения с эталоном). */
    private static String firstWordLettersForFuzzy(String trimmed) {
        java.util.regex.Matcher m = Pattern.compile("^\\s*(\\S+)").matcher(trimmed);
        if (!m.find()) {
            return "";
        }
        String w = m.group(1).toLowerCase(Locale.ROOT);
        while (!w.isEmpty() && !Character.isLetter(w.charAt(0))) {
            w = w.substring(1);
        }
        while (!w.isEmpty() && !Character.isLetter(w.charAt(w.length() - 1))) {
            w = w.substring(0, w.length() - 1);
        }
        return w;
    }

    private static int levenshtein(String a, String b) {
        if (a == null) {
            a = "";
        }
        if (b == null) {
            b = "";
        }
        int n = a.length();
        int m = b.length();
        int[] row = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            row[j] = j;
        }
        for (int i = 1; i <= n; i++) {
            int prevDiag = row[0];
            row[0] = i;
            for (int j = 1; j <= m; j++) {
                int temp = row[j];
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                row[j] = Math.min(Math.min(row[j] + 1, row[j - 1] + 1), prevDiag + cost);
                prevDiag = temp;
            }
        }
        return row[m];
    }

    /**
     * Строка похожа на заголовок таблицы, но не соответствует шаблону «Таблица …» (сокращение, опечатка).
     */
    private static boolean isLikelyMisspelledTableTitle(String trimmed) {
        if (trimmed == null || trimmed.isBlank()) {
            return false;
        }
        if (TABLE_TITLE_LINE.matcher(trimmed).find()) {
            return false;
        }
        if (TABLE_TITLE_ABBREV_TYPO.matcher(trimmed).find()) {
            return true;
        }
        if (TABLE_TITLE_TYPO_TABLITC.matcher(trimmed).find()) {
            return true;
        }
        String w = firstWordLettersForFuzzy(trimmed);
        if (w.length() < 4) {
            return false;
        }
        if (w.startsWith("table")) {
            int d = levenshtein(w, "table");
            return d > 0 && d <= 2;
        }
        if (w.startsWith("табл")) {
            if (w.equals("таблица")) {
                return false;
            }
            if (w.length() <= 5 && containsDigit(trimmed)) {
                return true;
            }
            return levenshtein(w, "таблица") <= 2 && w.length() >= 5;
        }
        return levenshtein(w, "таблица") <= 2 && w.length() >= 5;
    }

    private static boolean looksLikeFigureWordTypo(String trimmed) {
        String low = trimmed.toLowerCase(Locale.ROOT).trim();
        if (!low.startsWith("рис") && !low.startsWith("fig")) {
            return false;
        }
        String w = firstWordLettersForFuzzy(trimmed);
        if (w.length() < 4) {
            return false;
        }
        return levenshtein(w, "рисунок") <= 2 || (w.length() >= 4 && levenshtein(w, "figure") <= 2);
    }

    /**
     * Подпись к рисунку выглядит как «Рис. …» / «Рисунок …», но номер не извлекается по шаблону, или слово с опечаткой.
     */
    private static boolean isMisspelledFigureCaptionParagraph(String trimmed) {
        if (trimmed == null || trimmed.isBlank()) {
            return false;
        }
        if (!containsDigit(trimmed)) {
            return false;
        }
        if (FIGURE_NUMBER.matcher(trimmed).find()) {
            return false;
        }
        if (FIGURE_CAPTION_LINE.matcher(trimmed).find()) {
            return true;
        }
        return looksLikeFigureWordTypo(trimmed);
    }

    /** Пропуск проверки шрифта/размера/интервала для заголовков, таблиц, подписей. */
    private boolean shouldSkipFormattingCheck(XWPFParagraph p, XWPFParagraph previous) {
        String style = p.getStyle();
        if (style != null && !style.isBlank() && HEADING_STYLE.matcher(style).find()) return true;
        String text = p.getText();
        if (text != null && !text.isBlank()) {
            String t = text.trim();
            if (SECTION_HEADING.matcher(t).find()) return true;
            if (FIGURE_CAPTION.matcher(t).find() || FIGURE_CAPTION_LINE.matcher(t).find()
                    || TABLE_CAPTION.matcher(t).find() || TABLE_TITLE_LINE.matcher(t).find()) return true;
            if (isLikelyMisspelledTableTitle(t) || isMisspelledFigureCaptionParagraph(t)) return true;
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

    /**
     * «Уровень: Основной текст» в Word — в OOXML нет уровня структуры 0–8 ({@code w:outlineLvl}) на абзаце/стиле,
     * либо значение вне 0–8. Уровни 0–8 соответствуют заголовкам 1–9 — их не проверяем на шрифт/размер.
     * Учитывается цепочка стилей ({@code basedOn}), если у абзаца нет своего {@code outlineLvl}.
     */
    private boolean isOutlineLevelBodyText(XWPFParagraph p, XWPFDocument doc) {
        Integer direct = getDirectOutlineLvl(p);
        if (direct != null) {
            return direct < 0 || direct > 8;
        }
        Integer fromStyle = getOutlineLvlFromStyleChain(p, doc);
        if (fromStyle != null) {
            return fromStyle < 0 || fromStyle > 8;
        }
        return true;
    }

    private static Integer getDirectOutlineLvl(XWPFParagraph p) {
        try {
            CTP ctp = p.getCTP();
            if (ctp == null) {
                return null;
            }
            CTPPr pPr = ctp.getPPr();
            if (pPr == null || !pPr.isSetOutlineLvl()) {
                return null;
            }
            BigInteger v = pPr.getOutlineLvl().getVal();
            return v == null ? null : v.intValue();
        } catch (Exception e) {
            return null;
        }
    }

    private Integer getOutlineLvlFromStyleChain(XWPFParagraph p, XWPFDocument doc) {
        if (doc == null) {
            return null;
        }
        XWPFStyles styles = doc.getStyles();
        if (styles == null) {
            return null;
        }
        String id = p.getStyleID();
        if (id == null || id.isBlank()) {
            id = p.getStyle();
        }
        return getOutlineLvlFromStyle(id, styles, new HashSet<>());
    }

    private Integer getOutlineLvlFromStyle(String styleId, XWPFStyles styles, Set<String> seen) {
        if (styleId == null || styleId.isBlank() || !seen.add(styleId)) {
            return null;
        }
        XWPFStyle s = styles.getStyle(styleId);
        if (s == null) {
            return null;
        }
        CTStyle ct = s.getCTStyle();
        if (ct == null) {
            return null;
        }
        CTPPrGeneral ppr = ct.getPPr();
        if (ppr != null && ppr.isSetOutlineLvl() && ppr.getOutlineLvl().getVal() != null) {
            return ppr.getOutlineLvl().getVal().intValue();
        }
        if (ct.isSetBasedOn() && ct.getBasedOn().getVal() != null) {
            return getOutlineLvlFromStyle(ct.getBasedOn().getVal(), styles, seen);
        }
        return null;
    }

    public CriteriaResultDto checkFontDocx(XWPFDocument doc, String expectedFont, List<XWPFRun> toHighlight) {
        if (expectedFont == null || expectedFont.isBlank()) return passed("FONT", "Шрифт", "Проверка не задана");
        String normalizedExpected = normalizeFont(expectedFont);
        List<XWPFRun> wrongRuns = new ArrayList<>();
        List<XWPFParagraph> paragraphs = doc.getParagraphs();
        for (int i = 0; i < paragraphs.size(); i++) {
            XWPFParagraph p = paragraphs.get(i);
            XWPFParagraph prev = i > 0 ? paragraphs.get(i - 1) : null;
            if (shouldSkipFormattingCheck(p, prev)) continue;
            if (!isOutlineLevelBodyText(p, doc)) continue;
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
                XWPFParagraph p = paragraphs.get(i);
                if (shouldSkipFormattingCheck(p, i > 0 ? paragraphs.get(i - 1) : null)) continue;
                if (!isOutlineLevelBodyText(p, doc)) continue;
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
            XWPFParagraph p = paragraphs.get(i);
            if (shouldSkipFormattingCheck(p, i > 0 ? paragraphs.get(i - 1) : null)) continue;
            if (!isOutlineLevelBodyText(p, doc)) continue;
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

    public CriteriaResultDto checkFontSizeDocx(XWPFDocument doc, String expectedSize, List<XWPFRun> toHighlight) {
        if (expectedSize == null || expectedSize.isBlank()) return passed("FONT_SIZE", "Размер шрифта", "Проверка не задана");
        double expectedPt = parseFontSize(expectedSize);
        if (expectedPt <= 0) return passed("FONT_SIZE", "Размер шрифта", "Критерий не распознан");
        List<XWPFRun> wrongRuns = new ArrayList<>();
        List<XWPFParagraph> paragraphs = doc.getParagraphs();
        for (int i = 0; i < paragraphs.size(); i++) {
            XWPFParagraph p = paragraphs.get(i);
            if (shouldSkipFormattingCheck(p, i > 0 ? paragraphs.get(i - 1) : null)) continue;
            if (!isOutlineLevelBodyText(p, doc)) continue;
            for (XWPFRun r : p.getRuns()) {
                if (isRunTextEmpty(r)) {
                    continue;
                }
                Double sz = r.getFontSizeAsDouble();
                if (sz != null && sz > 0 && Math.abs(sz - expectedPt) >= FONT_SIZE_TOLERANCE_PT) {
                    wrongRuns.add(r);
                }
            }
        }
        boolean ok = wrongRuns.isEmpty();
        if (!ok) toHighlight.addAll(wrongRuns);
        Set<Double> sizes = new HashSet<>();
        for (int i = 0; i < paragraphs.size(); i++) {
            XWPFParagraph p = paragraphs.get(i);
            if (shouldSkipFormattingCheck(p, i > 0 ? paragraphs.get(i - 1) : null)) continue;
            if (!isOutlineLevelBodyText(p, doc)) continue;
            for (XWPFRun r : p.getRuns()) {
                if (isRunTextEmpty(r)) {
                    continue;
                }
                Double sz = r.getFontSizeAsDouble();
                if (sz != null && sz > 0) sizes.add(sz);
            }
        }
        double avg = sizes.isEmpty() ? 0 : sizes.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double minSz = sizes.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double maxSz = sizes.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        String actualStr = formatFontSizeActual(sizes, avg, minSz, maxSz, wrongRuns, expectedPt);
        return CriteriaResultDto.builder()
                .code("FONT_SIZE").title("Размер шрифта").passed(ok)
                .message(ok ? "Размер соответствует" : "Обнаружен размер шрифта, отличный от " + expectedSize)
                .expected(expectedSize).actual(actualStr)
                .level(ok ? null : "warning").build();
    }

    private static boolean isRunTextEmpty(XWPFRun r) {
        if (r == null) {
            return true;
        }
        String t = r.getText(0);
        return t == null || t.isBlank();
    }

    /**
     * Среднее с округлением до целого давало «14 пт» при одном куске 11–12 пт среди основного 14 пт — визуально как совпадение при failed.
     */
    private static String formatFontSizeActual(Set<Double> sizes, double avg, double minSz, double maxSz,
                                               List<XWPFRun> wrongRuns, double expectedPt) {
        if (sizes.isEmpty()) {
            return "не определён";
        }
        if (sizes.size() == 1 || Math.abs(maxSz - minSz) <= 0.5) {
            return String.format("%.0f пт", avg);
        }
        String base = String.format("%.0f–%.0f пт (в тексте несколько размеров)", minSz, maxSz);
        if (wrongRuns == null || wrongRuns.isEmpty()) {
            return base;
        }
        Set<Double> offending = new TreeSet<>();
        for (XWPFRun run : wrongRuns) {
            Double s = run.getFontSizeAsDouble();
            if (s != null && s > 0 && Math.abs(s - expectedPt) >= FONT_SIZE_TOLERANCE_PT) {
                offending.add(s);
            }
        }
        if (offending.isEmpty()) {
            return base;
        }
        return base + "; не в норме: "
                + offending.stream().map(z -> String.format("%.0f пт", z)).collect(Collectors.joining(", "));
    }

    public CriteriaResultDto checkLineSpacingDocx(XWPFDocument doc, String expectedSpacing, List<XWPFRun> toHighlight) {
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
    public record PdfFontInfo(Set<String> fontNames, Set<Float> fontSizesPt) {}

    private boolean shouldSkipForFontCheck(String text) {
        if (text == null || text.isBlank()) return false;
        String t = text.trim();
        if (FIGURE_CAPTION.matcher(t).find() || FIGURE_CAPTION_LINE.matcher(t).find()
                || TABLE_CAPTION.matcher(t).find() || TABLE_TITLE_LINE.matcher(t).find()) return true;
        if (isLikelyMisspelledTableTitle(t) || isMisspelledFigureCaptionParagraph(t)) return true;
        if (SECTION_HEADING.matcher(t).find()) return true;
        if (t.length() < 150 && NUMBERED_HEADING.matcher(t).find()) return true;
        return false;
    }

    public PdfFontInfo extractPdfFontInfo(PDDocument doc) throws IOException {
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

    public CriteriaResultDto checkFontPdf(PdfFontInfo info, String expectedFont) {
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

    public CriteriaResultDto checkFontSizePdf(PdfFontInfo info, String expectedSize) {
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

    public CriteriaResultDto checkLineSpacingPdf(PDDocument doc, String expectedSpacing) {
        return CriteriaResultDto.builder().code("LINE_SPACING").title("Межстрочный интервал").passed(true)
                .message("Проверка интервала в PDF недоступна (анализ позиций текста)")
                .expected(expectedSpacing).actual("—").level(null).build();
    }

    public CriteriaResultDto checkMinPages(int actual, int minRequired) {
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

    public CriteriaResultDto checkMinSources(int actual, int minRequired) {
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

    public List<CriteriaResultDto> checkStructureDocx(XWPFDocument doc, TemplateCriteriaDto t) {
        return checkStructureDocx(doc, t, null);
    }

    /**
     * @param runsToHighlight если не {@code null}, в абзацы с ошибками выравнивания подписей «Рис.» / заголовков «Таблица …»
     *                      добавляются раны для красной подсветки в файле с замечаниями.
     */
    public List<CriteriaResultDto> checkStructureDocx(XWPFDocument doc, TemplateCriteriaDto t, List<XWPFRun> runsToHighlight) {
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
        List<CriteriaResultDto> list = checkStructure(full, t);
        list.addAll(checkIllustrationsAndTablesDocx(doc, t, runsToHighlight));
        return list;
    }

    /**
     * Проверки по шаблону: нумерация, выравнивание подписей к рисункам, «подпись под рисунком»
     * (если в шаблоне задана позиция рисунка (центр / слева / справа) — по выравниванию абзацев «Рис.» в основном тексте;
     * иначе — картинка в предыдущем абзаце тела по структуре DOCX),
     * заголовок «Таблица …» над или под таблицей ({@code tableTitlePlacement}), выравнивание заголовка (например «Справа»).
     *
     * @param runsToHighlight опционально: раны неверно выровненных подписей и заголовков таблиц для аннотации DOCX
     */
    public List<CriteriaResultDto> checkIllustrationsAndTablesDocx(XWPFDocument doc, TemplateCriteriaDto t) {
        return checkIllustrationsAndTablesDocx(doc, t, null);
    }

    public List<CriteriaResultDto> checkIllustrationsAndTablesDocx(XWPFDocument doc, TemplateCriteriaDto t, List<XWPFRun> runsToHighlight) {
        List<CriteriaResultDto> results = new ArrayList<>();
        String illNum = t.getIllNumbering();
        String figPos = t.getFigurePosition();
        String figCap = t.getFigureCaption();
        String tblTitle = t.getTableTitle();
        String tblPos = t.getTablePosition();
        String tblPlacement = t.getTableTitlePlacement();
        if ((illNum == null || illNum.isBlank()) && (figPos == null || figPos.isBlank())
                && (figCap == null || figCap.isBlank()) && (tblTitle == null || tblTitle.isBlank())
                && (tblPos == null || tblPos.isBlank())
                && (tblPlacement == null || tblPlacement.isBlank())) {
            return results;
        }

        List<Integer> figureNumbers = new ArrayList<>();
        List<Integer> tableNumbers = new ArrayList<>();
        List<XWPFParagraph> figureCaptionParagraphs = new ArrayList<>();
        /** Все абзацы с текстом «Таблица …» / «table …» — тело документа и ячейки (как для {@link #TABLE_TITLE}). */
        List<XWPFParagraph> tableTitleParagraphs = new ArrayList<>();
        List<XWPFParagraph> tableTypoParagraphs = new ArrayList<>();
        List<XWPFParagraph> figureTypoParagraphs = new ArrayList<>();

        for (var elem : doc.getBodyElements()) {
            if (elem instanceof XWPFParagraph p) {
                String line = p.getText();
                if (line != null && !line.isBlank()) {
                    String trimmed = line.trim();
                    if (FIGURE_CAPTION_LINE.matcher(trimmed).find()) {
                        if (FIGURE_NUMBER.matcher(trimmed).find()) {
                            extractNumbers(FIGURE_NUMBER, trimmed, figureNumbers);
                            figureCaptionParagraphs.add(p);
                        } else if (containsDigit(trimmed)) {
                            figureTypoParagraphs.add(p);
                        }
                    } else if (isMisspelledFigureCaptionParagraph(trimmed)) {
                        figureTypoParagraphs.add(p);
                    }
                    if (TABLE_TITLE_LINE.matcher(trimmed).find()) {
                        extractNumbers(TABLE_NUMBER, trimmed, tableNumbers);
                        tableTitleParagraphs.add(p);
                    } else if (isLikelyMisspelledTableTitle(trimmed)) {
                        tableTypoParagraphs.add(p);
                    }
                }
            } else if (elem instanceof XWPFTable table) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        for (XWPFParagraph cellP : cell.getParagraphs()) {
                            String line = cellP.getText();
                            if (line != null && !line.isBlank()) {
                                String trimmed = line.trim();
                                if (FIGURE_CAPTION_LINE.matcher(trimmed).find()) {
                                    if (FIGURE_NUMBER.matcher(trimmed).find()) {
                                        extractNumbers(FIGURE_NUMBER, trimmed, figureNumbers);
                                        figureCaptionParagraphs.add(cellP);
                                    } else if (containsDigit(trimmed)) {
                                        figureTypoParagraphs.add(cellP);
                                    }
                                } else if (isMisspelledFigureCaptionParagraph(trimmed)) {
                                    figureTypoParagraphs.add(cellP);
                                }
                                if (TABLE_TITLE_LINE.matcher(trimmed).find()) {
                                    extractNumbers(TABLE_NUMBER, trimmed, tableNumbers);
                                    tableTitleParagraphs.add(cellP);
                                } else if (isLikelyMisspelledTableTitle(trimmed)) {
                                    tableTypoParagraphs.add(cellP);
                                }
                            }
                        }
                    }
                }
            }
        }

        List<IBodyElement> body = doc.getBodyElements();
        boolean foundBodyFigureCaption = false;
        boolean allBodyFigureCaptionsHaveImageAbove = true;
        boolean allBodyFigureCaptionsHaveImageBelow = true;
        /** Для «Под рисунком» + заданной позиции рисунка в шаблоне — выравнивание подписей в основном тексте (а не только «картинка в предыдущем абзаце»). */
        boolean allBodyFigureCaptionsAlignOk = true;
        for (int i = 0; i < body.size(); i++) {
            if (!(body.get(i) instanceof XWPFParagraph p)) {
                continue;
            }
            String line = p.getText();
            if (line == null || line.isBlank()) {
                continue;
            }
            String trimmed = line.trim();
            if (FIGURE_CAPTION_LINE.matcher(trimmed).find()) {
                foundBodyFigureCaption = true;
                if (appliesFigurePositionCheck(figPos) && !paragraphAlignmentMatchesExpected(p, figPos)) {
                    allBodyFigureCaptionsAlignOk = false;
                }
                if (!previousBodyParagraphHasImage(body, i)) {
                    allBodyFigureCaptionsHaveImageAbove = false;
                }
                if (!nextBodyParagraphHasImage(body, i)) {
                    allBodyFigureCaptionsHaveImageBelow = false;
                }
            }
        }

        boolean foundBodyTableTitle = false;
        boolean allTableTitlesFollowedByTable = true;
        boolean allTableTitlesPrecededByTable = true;
        boolean expectTitleAbove = tableTitleExpectsAbove(tblPlacement);
        boolean expectTitleBelow = tableTitleExpectsBelow(tblPlacement);
        for (int i = 0; i < body.size(); i++) {
            if (!(body.get(i) instanceof XWPFParagraph p)) {
                continue;
            }
            String line = p.getText();
            if (line == null || line.isBlank()) {
                continue;
            }
            String trimmed = line.trim();
            if (TABLE_TITLE_LINE.matcher(trimmed).find()) {
                foundBodyTableTitle = true;
                if (expectTitleAbove && !nextBodyElementIsTable(body, i)) {
                    allTableTitlesFollowedByTable = false;
                }
                if (expectTitleBelow && !previousBodyElementIsTable(body, i)) {
                    allTableTitlesPrecededByTable = false;
                }
            }
        }

        boolean anyTableTitleForAlign = !tableTitleParagraphs.isEmpty();
        boolean allTableTitlesAlignOk = true;
        if (tblPos != null && !tblPos.isBlank()) {
            for (XWPFParagraph p : tableTitleParagraphs) {
                if (!paragraphAlignmentMatchesExpected(p, tblPos)) {
                    allTableTitlesAlignOk = false;
                    addParagraphRunsToHighlight(p, runsToHighlight);
                }
            }
        }

        if (illustrationNumberingIsContinuous(illNum)) {
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
        } else if (illustrationNumberingIsPerSection(illNum)) {
            List<List<Integer>> bySection = collectFigureNumbersPerSectionDocx(body, doc);
            boolean ok = perSectionsFigureNumberingOk(bySection);
            results.add(CriteriaResultDto.builder()
                    .code("ILL_NUMBERING_BY_SECTION")
                    .title("Нумерация иллюстраций по разделам")
                    .passed(ok)
                    .message(ok
                            ? "В каждом разделе нумерация иллюстраций с 1 и подряд (1, 2, 3, …)"
                            : "Нарушена нумерация «по разделам»: в одном из разделов номера не 1, 2, 3, … подряд")
                    .expected(illNum != null ? illNum.trim() : "По разделам")
                    .actual(ok ? "OK" : summarizeSectionNumberingFailure(bySection))
                    .level(ok ? null : "warning")
                    .build());
        }
        if (appliesFigurePositionCheck(figPos)) {
            String exp = figPos.trim();
            boolean allOk = figureCaptionParagraphs.isEmpty()
                    || figureCaptionParagraphs.stream().allMatch(p -> paragraphAlignmentMatchesExpected(p, figPos));
            if (!allOk) {
                highlightMisalignedFigureCaptions(figPos, figureCaptionParagraphs, runsToHighlight);
            }
            results.add(CriteriaResultDto.builder()
                    .code("FIGURE_POSITION")
                    .title("Позиция рисунка")
                    .passed(allOk)
                    .message(allOk ? ("Подписи рисунков: " + exp) : "Не все подписи «Рис.» выровнены как в шаблоне")
                    .expected(exp)
                    .actual(allOk ? exp : "Проверьте выравнивание абзацев с подписями")
                    .level(allOk ? null : "warning")
                    .build());
        }
        if (figCap != null && containsIgnoreCase(figCap, "под рисунком")) {
            boolean hasCaptions = !figureNumbers.isEmpty();
            boolean ok;
            String actual;
            String expected = "Под рисунком";
            String messageOk;
            String messageFail;
            if (foundBodyFigureCaption) {
                if (appliesFigurePositionCheck(figPos)) {
                    ok = allBodyFigureCaptionsAlignOk;
                    expected = figPos.trim();
                    messageOk = "Подписи «Рис.» в основном тексте выровнены как в шаблоне";
                    messageFail = "Не все подписи «Рис./рис.» в основном тексте соответствуют выравниванию «" + figPos.trim() + "»";
                    actual = ok ? figPos.trim() : "Проверьте выравнивание абзацев с подписями в основном тексте";
                } else {
                    ok = allBodyFigureCaptionsHaveImageAbove;
                    messageOk = "Подпись под рисунком (по структуре DOCX)";
                    messageFail = "Ожидался абзац с картинкой непосредственно перед подписью в основном тексте";
                    actual = ok ? "Над подписью есть абзац с изображением" : "Не у всех подписей «Рис./рис.» выше по тексту есть рисунок";
                }
            } else {
                ok = hasCaptions;
                if (ok) {
                    messageOk = "Подписи найдены (вне основного тела проверка «под рисунком» не выполнялась)";
                    messageFail = "";
                    actual = "Подписи найдены (вне основного тела проверка «под рисунком» не выполнялась)";
                } else {
                    messageOk = "";
                    messageFail = "Подписи рисунков не найдены";
                    actual = "Не найдено";
                }
            }
            results.add(CriteriaResultDto.builder()
                    .code("FIGURE_CAPTION_POS")
                    .title("Подпись рисунка")
                    .passed(ok)
                    .message(ok ? messageOk : messageFail)
                    .expected(expected)
                    .actual(actual)
                    .level(ok ? null : "warning")
                    .build());
        }
        if (figureCaptionExpectsAbove(figCap)) {
            boolean hasCaptions = !figureNumbers.isEmpty();
            boolean ok;
            String actual;
            if (foundBodyFigureCaption) {
                ok = allBodyFigureCaptionsHaveImageBelow;
                actual = ok ? "Под подписью есть абзац с изображением" : "Не у всех подписей «Рис./Рисунок» ниже по тексту есть рисунок";
            } else {
                ok = hasCaptions;
                actual = hasCaptions ? "Подписи найдены (вне основного тела проверка «над рисунком» не выполнялась)" : "Не найдено";
            }
            results.add(CriteriaResultDto.builder()
                    .code("FIGURE_CAPTION_ABOVE")
                    .title("Подпись рисунка (над рисунком)")
                    .passed(ok)
                    .message(ok ? "Подпись над рисунком (по структуре DOCX)" : (foundBodyFigureCaption
                            ? "Ожидался абзац с картинкой непосредственно после подписи в основном тексте"
                            : "Подписи рисунков не найдены"))
                    .expected("Над рисунком")
                    .actual(actual)
                    .level(ok ? null : "warning")
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
        if (tblTitle != null && !tblTitle.isBlank() && expectTitleAbove) {
            boolean placementOk;
            String placementActual;
            String placementLevel;
            if (!foundBodyTableTitle) {
                placementOk = true;
                if (anyTableTitleForAlign) {
                    placementActual = "Заголовки «Таблица …» не в основном потоке абзацев тела — порядок «строка, затем таблица» по телу не проверялся";
                    placementLevel = "info";
                } else {
                    placementActual = "Строки «Таблица …» не найдены";
                    placementLevel = "info";
                }
            } else {
                placementOk = allTableTitlesFollowedByTable;
                placementActual = placementOk ? "OK" : "Проверьте порядок: заголовок, затем таблица";
                placementLevel = placementOk ? null : "warning";
            }
            results.add(CriteriaResultDto.builder()
                    .code("TABLE_AFTER_TITLE")
                    .title("Название таблицы над таблицей")
                    .passed(placementOk)
                    .message(placementOk
                            ? (foundBodyTableTitle
                                    ? "После строки «Таблица …» в теле документа следует таблица"
                                    : "Нет абзаца «Таблица …» в основном тексте для проверки порядка")
                            : "После одной из строк «Таблица …» нет следующего блока таблицы")
                    .expected("Над таблицей")
                    .actual(placementActual)
                    .level(placementLevel)
                    .build());
        }
        if (tblTitle != null && !tblTitle.isBlank() && expectTitleBelow) {
            boolean placementOk;
            String placementActual;
            String placementLevel;
            if (!foundBodyTableTitle) {
                placementOk = true;
                if (anyTableTitleForAlign) {
                    placementActual = "Заголовки «Таблица …» не в основном потоке абзацев тела — порядок «таблица, затем строка» по телу не проверялся";
                    placementLevel = "info";
                } else {
                    placementActual = "Строки «Таблица …» не найдены";
                    placementLevel = "info";
                }
            } else {
                placementOk = allTableTitlesPrecededByTable;
                placementActual = placementOk ? "OK" : "Проверьте порядок: таблица, затем заголовок";
                placementLevel = placementOk ? null : "warning";
            }
            results.add(CriteriaResultDto.builder()
                    .code("TABLE_TITLE_BELOW_TABLE")
                    .title("Название таблицы под таблицей")
                    .passed(placementOk)
                    .message(placementOk
                            ? (foundBodyTableTitle
                                    ? "Перед строкой «Таблица …» в теле документа идёт таблица"
                                    : "Нет абзаца «Таблица …» в основном тексте для проверки порядка")
                            : "Перед одной из строк «Таблица …» нет предыдущего блока таблицы")
                    .expected("Под таблицей")
                    .actual(placementActual)
                    .level(placementLevel)
                    .build());
        }
        if (tblPos != null && !tblPos.isBlank()) {
            boolean hasTableTitleLines = anyTableTitleForAlign;
            boolean alignOk = !hasTableTitleLines || allTableTitlesAlignOk;
            results.add(CriteriaResultDto.builder()
                    .code("TABLE_POSITION")
                    .title("Позиция заголовка таблицы")
                    .passed(alignOk)
                    .message(alignOk
                            ? (hasTableTitleLines
                                    ? "Выравнивание заголовков «Таблица …» соответствует шаблону"
                                    : "Строк «Таблица …» не найдено — выравнивание не проверялось")
                            : "Выравнивание одного из заголовков «Таблица …» не совпадает с шаблоном")
                    .expected(tblPos.trim())
                    .actual(alignOk
                            ? (hasTableTitleLines ? tblPos.trim() : "Нет абзацев «Таблица …»")
                            : "См. абзацы «Таблица …» в документе")
                    .level(!hasTableTitleLines ? "info" : (alignOk ? null : "warning"))
                    .build());
        }
        if (!tableTypoParagraphs.isEmpty()) {
            for (XWPFParagraph p : tableTypoParagraphs) {
                addParagraphRunsToHighlight(p, runsToHighlight);
            }
            String sample = paragraphTextSnippet(tableTypoParagraphs.get(0));
            results.add(CriteriaResultDto.builder()
                    .code("TABLE_TITLE_TYPO")
                    .title("Заголовок таблицы: опечатка / сокращение")
                    .passed(false)
                    .message("Строка похожа на название таблицы, но написана не как «Таблица N» — автоматическая проверка заголовков и нумерации таблиц эту строку не учитывает")
                    .expected("Таблица N …")
                    .actual(tableTypoParagraphs.size() + " абз., пример: «" + sample + "»")
                    .level("warning")
                    .build());
        }
        if (!figureTypoParagraphs.isEmpty()) {
            for (XWPFParagraph p : figureTypoParagraphs) {
                addParagraphRunsToHighlight(p, runsToHighlight);
            }
            String sample = paragraphTextSnippet(figureTypoParagraphs.get(0));
            results.add(CriteriaResultDto.builder()
                    .code("FIGURE_CAPTION_TYPO")
                    .title("Подпись к рисунку: опечатка")
                    .passed(false)
                    .message("Есть цифра, но подпись не распознана как «Рис. N» / «Рисунок N» — нумерация и проверки по подписям эту строку пропускают")
                    .expected("Рис. N … / Рисунок N …")
                    .actual(figureTypoParagraphs.size() + " абз., пример: «" + sample + "»")
                    .level("warning")
                    .build());
        }
        return results;
    }

    private static String paragraphTextSnippet(XWPFParagraph p) {
        if (p == null) {
            return "";
        }
        String t = p.getText();
        if (t == null) {
            return "";
        }
        t = t.trim().replace('\r', ' ').replace('\n', ' ');
        if (t.length() > 100) {
            return t.substring(0, 97) + "...";
        }
        return t;
    }

    /** В предыдущих абзацах тела: первый с текстом или с картинкой; картинка должна быть сразу перед подписью (пропускаем пустые абзацы без изображений). */
    private boolean previousBodyParagraphHasImage(List<IBodyElement> body, int captionIndex) {
        for (int j = captionIndex - 1; j >= 0; j--) {
            if (!(body.get(j) instanceof XWPFParagraph prevP)) {
                return false;
            }
            if (paragraphHasPicture(prevP)) {
                return true;
            }
            if (!isParagraphTextEmpty(prevP)) {
                return false;
            }
        }
        return false;
    }

    /** Следующие абзацы тела после подписи: первый значимый — с картинкой (подпись «над рисунком»). */
    private boolean nextBodyParagraphHasImage(List<IBodyElement> body, int captionIndex) {
        for (int j = captionIndex + 1; j < body.size(); j++) {
            IBodyElement e = body.get(j);
            if (e instanceof XWPFTable) {
                return false;
            }
            if (!(e instanceof XWPFParagraph nextP)) {
                return false;
            }
            if (paragraphHasPicture(nextP)) {
                return true;
            }
            if (!isParagraphTextEmpty(nextP)) {
                return false;
            }
        }
        return false;
    }

    private static boolean isParagraphTextEmpty(XWPFParagraph p) {
        String t = p.getText();
        return t == null || t.isBlank();
    }

    /** Все раны абзаца — для красной подсветки в DOCX с замечаниями (выравнивание подписей / заголовков таблиц). */
    private static void addParagraphRunsToHighlight(XWPFParagraph p, List<XWPFRun> runsToHighlight) {
        if (p == null || runsToHighlight == null) {
            return;
        }
        for (XWPFRun r : p.getRuns()) {
            runsToHighlight.add(r);
        }
    }

    private void highlightMisalignedFigureCaptions(String figPos, List<XWPFParagraph> captions, List<XWPFRun> runsToHighlight) {
        if (runsToHighlight == null) {
            return;
        }
        for (XWPFParagraph p : captions) {
            if (!paragraphAlignmentMatchesExpected(p, figPos)) {
                addParagraphRunsToHighlight(p, runsToHighlight);
            }
        }
    }

    private boolean paragraphHasPicture(XWPFParagraph p) {
        if (p == null) {
            return false;
        }
        for (XWPFRun r : p.getRuns()) {
            try {
                if (r.getEmbeddedPictures() != null && !r.getEmbeddedPictures().isEmpty()) {
                    return true;
                }
            } catch (Exception ignored) {
            }
            try {
                if (r.getCTR() != null && !r.getCTR().getDrawingList().isEmpty()) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    /** Следующий значимый блок тела после абзаца с индексом idx — {@link XWPFTable} (пустые абзацы пропускаем). */
    private boolean nextBodyElementIsTable(List<IBodyElement> body, int idx) {
        for (int j = idx + 1; j < body.size(); j++) {
            IBodyElement e = body.get(j);
            if (e instanceof XWPFTable) {
                return true;
            }
            if (e instanceof XWPFParagraph p) {
                if (!isParagraphTextEmpty(p)) {
                    return false;
                }
            }
        }
        return false;
    }

    /** Предыдущий значимый блок тела перед абзацем с индексом idx — {@link XWPFTable} (пустые абзацы пропускаем). */
    private boolean previousBodyElementIsTable(List<IBodyElement> body, int idx) {
        for (int j = idx - 1; j >= 0; j--) {
            IBodyElement e = body.get(j);
            if (e instanceof XWPFTable) {
                return true;
            }
            if (e instanceof XWPFParagraph p) {
                if (!isParagraphTextEmpty(p)) {
                    return false;
                }
            }
        }
        return false;
    }

    /** По умолчанию и по ГОСТ чаще: название над таблицей. */
    private static boolean tableTitleExpectsAbove(String placement) {
        return !tableTitleExpectsBelow(placement);
    }

    /** «Под таблицей» / below table — подпись-название после таблицы. */
    private static boolean tableTitleExpectsBelow(String placement) {
        if (placement == null || placement.isBlank()) {
            return false;
        }
        String s = placement.toLowerCase(Locale.ROOT);
        if (s.contains("под таблиц") || s.contains("ниже таблиц") || s.contains("после таблиц")) {
            return true;
        }
        return s.contains("below table") || s.contains("after table");
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        if (haystack == null || needle == null) {
            return false;
        }
        return haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private static boolean figureCaptionExpectsAbove(String figCap) {
        if (figCap == null || figCap.isBlank()) {
            return false;
        }
        String s = figCap.toLowerCase(Locale.ROOT);
        return s.contains("над рисунк") || s.contains("above figure") || s.contains("above the figure");
    }

    private static boolean illustrationNumberingIsContinuous(String illNum) {
        if (illNum == null || illNum.isBlank()) {
            return false;
        }
        String s = illNum.toLowerCase(Locale.ROOT);
        return s.contains("сквозн");
    }

    /** «По разделам», «в рамках раздела» и т.п. — не сквозная. */
    private static boolean illustrationNumberingIsPerSection(String illNum) {
        if (illNum == null || illNum.isBlank()) {
            return false;
        }
        String s = illNum.toLowerCase(Locale.ROOT);
        if (s.contains("сквозн")) {
            return false;
        }
        return s.contains("раздел");
    }

    private boolean isIllustrationSectionBoundaryParagraph(XWPFParagraph p, XWPFDocument doc) {
        String t = p.getText();
        if (t == null || t.isBlank()) {
            return false;
        }
        String trimmed = t.trim();
        if (SECTION_HEADING.matcher(trimmed).find()) {
            return true;
        }
        if (NUMBERED_HEADING.matcher(trimmed).find() && trimmed.length() < 200) {
            return true;
        }
        Integer ol = getDirectOutlineLvl(p);
        if (ol == null) {
            ol = getOutlineLvlFromStyleChain(p, doc);
        }
        return ol != null && ol >= 0 && ol <= 8;
    }

    private List<List<Integer>> collectFigureNumbersPerSectionDocx(List<IBodyElement> body, XWPFDocument doc) {
        List<List<Integer>> sections = new ArrayList<>();
        sections.add(new ArrayList<>());
        for (IBodyElement elem : body) {
            if (!(elem instanceof XWPFParagraph p)) {
                continue;
            }
            String line = p.getText();
            if (line == null || line.isBlank()) {
                continue;
            }
            String trimmed = line.trim();
            if (isIllustrationSectionBoundaryParagraph(p, doc)) {
                sections.add(new ArrayList<>());
            }
            if (FIGURE_CAPTION_LINE.matcher(trimmed).find()) {
                extractNumbers(FIGURE_NUMBER, trimmed, sections.get(sections.size() - 1));
            }
        }
        return sections;
    }

    private boolean perSectionsFigureNumberingOk(List<List<Integer>> bySection) {
        for (List<Integer> sec : bySection) {
            if (!sec.isEmpty() && !isContinuousNumbering(sec)) {
                return false;
            }
        }
        return true;
    }

    private static String summarizeSectionNumberingFailure(List<List<Integer>> bySection) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bySection.size(); i++) {
            List<Integer> sec = bySection.get(i);
            if (sec.isEmpty()) {
                continue;
            }
            if (!isContinuousNumberingStatic(sec)) {
                sb.append("раздел ").append(i + 1).append(": ").append(formatSectionNums(sec)).append("; ");
            }
        }
        return sb.isEmpty() ? "см. подписи Рис." : sb.toString().trim();
    }

    private static boolean isContinuousNumberingStatic(List<Integer> numbers) {
        if (numbers.isEmpty()) {
            return true;
        }
        List<Integer> sorted = new ArrayList<>(new LinkedHashSet<>(numbers));
        Collections.sort(sorted);
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i) != i + 1) {
                return false;
            }
        }
        return true;
    }

    private static String formatSectionNums(List<Integer> numbers) {
        List<Integer> sorted = new ArrayList<>(new LinkedHashSet<>(numbers));
        Collections.sort(sorted);
        return sorted.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }

    private static boolean isIllustrationSectionBoundaryText(String trimmed) {
        if (trimmed.isEmpty()) {
            return false;
        }
        if (SECTION_HEADING.matcher(trimmed).find()) {
            return true;
        }
        return NUMBERED_HEADING.matcher(trimmed).find() && trimmed.length() < 200;
    }

    private static List<List<Integer>> collectFigureNumbersPerSectionFromText(String fullText) {
        List<List<Integer>> sections = new ArrayList<>();
        sections.add(new ArrayList<>());
        for (String raw : fullText.split("\n")) {
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (isIllustrationSectionBoundaryText(trimmed)) {
                sections.add(new ArrayList<>());
            }
            if (FIGURE_CAPTION_LINE.matcher(trimmed).find()) {
                extractNumbersStatic(FIGURE_NUMBER, trimmed, sections.get(sections.size() - 1));
            }
        }
        return sections;
    }

    private static void extractNumbersStatic(Pattern pattern, String line, List<Integer> out) {
        java.util.regex.Matcher m = pattern.matcher(line);
        while (m.find()) {
            try {
                out.add(Integer.parseInt(m.group(1)));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    /** Сопоставление текста шаблона («Справа», «По центру», «Слева» и т.п.) с {@link ParagraphAlignment}. */
    private boolean paragraphAlignmentMatchesExpected(XWPFParagraph p, String templateText) {
        if (templateText == null || templateText.isBlank()) {
            return true;
        }
        String s = templateText.toLowerCase(Locale.ROOT);
        ParagraphAlignment a = p.getAlignment();
        if (s.contains("справа") || s.contains("right")) {
            return a == ParagraphAlignment.RIGHT;
        }
        if (s.contains("слева") || s.contains("left")) {
            return a == ParagraphAlignment.LEFT || a == ParagraphAlignment.BOTH || a == null;
        }
        if (s.contains("центр") || s.contains("центру") || s.contains("center") || s.contains("centre")) {
            return a == ParagraphAlignment.CENTER;
        }
        return true;
    }

    /** Есть ли в поле шаблона явное горизонтальное выравнивание для подписей к рисункам. */
    private static boolean appliesFigurePositionCheck(String figPos) {
        if (figPos == null || figPos.isBlank()) {
            return false;
        }
        String s = figPos.toLowerCase(Locale.ROOT);
        return s.contains("центр") || s.contains("центру") || s.contains("слева") || s.contains("справа")
                || s.contains("left") || s.contains("right") || s.contains("center") || s.contains("centre");
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

    public List<CriteriaResultDto> checkStructurePdf(PDDocument doc, TemplateCriteriaDto t) throws Exception {
        PDFTextStripper fullStripper = new PDFTextStripper();
        fullStripper.setSortByPosition(true);
        fullStripper.setAddMoreFormatting(true);
        String fullText = fullStripper.getText(doc);
        List<CriteriaResultDto> list = checkStructure(fullText, t);
        list.addAll(checkIllustrationsAndTablesFromText(fullText, t));
        return list;
    }

    public List<CriteriaResultDto> checkStructure(String fullText, TemplateCriteriaDto t) {
        List<CriteriaResultDto> list = new ArrayList<>();
        String lower = fullText.toLowerCase();
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

    /** Проверки иллюстраций и таблиц по полному тексту (PDF: без структуры абзац/таблица/картинка). */
    public List<CriteriaResultDto> checkIllustrationsAndTablesFromText(String fullText, TemplateCriteriaDto t) {
        List<CriteriaResultDto> results = new ArrayList<>();
        String illNum = t.getIllNumbering();
        String figPos = t.getFigurePosition();
        String figCap = t.getFigureCaption();
        String tblTitle = t.getTableTitle();
        String tblPos = t.getTablePosition();
        String tblPlacement = t.getTableTitlePlacement();
        if ((illNum == null || illNum.isBlank()) && (figPos == null || figPos.isBlank())
                && (figCap == null || figCap.isBlank()) && (tblTitle == null || tblTitle.isBlank())
                && (tblPos == null || tblPos.isBlank())
                && (tblPlacement == null || tblPlacement.isBlank())) {
            return results;
        }
        boolean wantsStructuralLayout = (figPos != null && !figPos.isBlank())
                || (figCap != null && containsIgnoreCase(figCap, "под рисунком"))
                || figureCaptionExpectsAbove(figCap)
                || (tblPos != null && !tblPos.isBlank())
                || (tblPlacement != null && !tblPlacement.isBlank());
        if (wantsStructuralLayout) {
            results.add(CriteriaResultDto.builder()
                    .code("PDF_LAYOUT_LIMIT")
                    .title("Расположение рисунков и таблиц (PDF)")
                    .passed(true)
                    .message("Для PDF по тексту проверяются нумерация и заголовки; положение подписи относительно рисунка и таблицы относительно заголовка по разметке автоматически не определяется.")
                    .expected("—")
                    .actual("Только текстовый анализ")
                    .level("info")
                    .build());
        }
        List<Integer> figureNumbers = new ArrayList<>();
        List<Integer> tableNumbers = new ArrayList<>();
        boolean tableTypoPdf = false;
        boolean figureTypoPdf = false;
        String tableTypoSample = "";
        String figureTypoSample = "";
        for (String line : fullText.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (TABLE_TITLE_LINE.matcher(trimmed).find()) {
                extractNumbers(TABLE_NUMBER, trimmed, tableNumbers);
            } else if (isLikelyMisspelledTableTitle(trimmed)) {
                tableTypoPdf = true;
                if (tableTypoSample.isEmpty()) {
                    tableTypoSample = trimmed.length() > 100 ? trimmed.substring(0, 97) + "..." : trimmed;
                }
            }
            if (FIGURE_CAPTION_LINE.matcher(trimmed).find()) {
                if (FIGURE_NUMBER.matcher(trimmed).find()) {
                    extractNumbers(FIGURE_NUMBER, trimmed, figureNumbers);
                } else if (containsDigit(trimmed)) {
                    figureTypoPdf = true;
                    if (figureTypoSample.isEmpty()) {
                        figureTypoSample = trimmed.length() > 100 ? trimmed.substring(0, 97) + "..." : trimmed;
                    }
                }
            } else if (isMisspelledFigureCaptionParagraph(trimmed)) {
                figureTypoPdf = true;
                if (figureTypoSample.isEmpty()) {
                    figureTypoSample = trimmed.length() > 100 ? trimmed.substring(0, 97) + "..." : trimmed;
                }
            }
        }
        if (tableTypoPdf) {
            results.add(CriteriaResultDto.builder()
                    .code("TABLE_TITLE_TYPO")
                    .title("Заголовок таблицы: опечатка / сокращение")
                    .passed(false)
                    .message("По тексту PDF: строка похожа на название таблицы, но не как «Таблица N»")
                    .expected("Таблица N …")
                    .actual(tableTypoSample.isEmpty() ? "См. текст" : "«" + tableTypoSample + "»")
                    .level("warning")
                    .build());
        }
        if (figureTypoPdf) {
            results.add(CriteriaResultDto.builder()
                    .code("FIGURE_CAPTION_TYPO")
                    .title("Подпись к рисунку: опечатка")
                    .passed(false)
                    .message("По тексту PDF: есть цифра, но строка не распознана как «Рис. N» / «Рисунок N»")
                    .expected("Рис. N … / Рисунок N …")
                    .actual(figureTypoSample.isEmpty() ? "См. текст" : "«" + figureTypoSample + "»")
                    .level("warning")
                    .build());
        }
        if (illustrationNumberingIsContinuous(illNum)) {
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
        } else if (illustrationNumberingIsPerSection(illNum)) {
            List<List<Integer>> bySection = collectFigureNumbersPerSectionFromText(fullText);
            boolean ok = bySection.stream().filter(s -> !s.isEmpty()).allMatch(DocumentValidationDelegate::isContinuousNumberingStatic);
            results.add(CriteriaResultDto.builder()
                    .code("ILL_NUMBERING_BY_SECTION")
                    .title("Нумерация иллюстраций по разделам")
                    .passed(ok)
                    .message(ok
                            ? "По тексту PDF: в каждом разделе номера иллюстраций с 1 подряд (эвристика по заголовкам)"
                            : "По тексту PDF: возможна ошибка нумерации по разделам (грубая проверка)")
                    .expected(illNum != null ? illNum.trim() : "По разделам")
                    .actual(ok ? "OK" : summarizeSectionNumberingFailure(bySection))
                    .level(ok ? null : "warning")
                    .build());
        }
        if (appliesFigurePositionCheck(figPos)) {
            results.add(CriteriaResultDto.builder()
                    .code("FIGURE_POSITION")
                    .title("Позиция рисунка")
                    .passed(true)
                    .message("Для PDF выравнивание подписей к рисункам по разметке не определяется")
                    .expected(figPos.trim())
                    .actual("Только текстовый анализ")
                    .level("info")
                    .build());
        }
        if (figCap != null && containsIgnoreCase(figCap, "под рисунком")) {
            boolean hasCaptions = !figureNumbers.isEmpty();
            results.add(CriteriaResultDto.builder()
                    .code("FIGURE_CAPTION_POS")
                    .title("Подпись рисунка")
                    .passed(hasCaptions)
                    .message(hasCaptions
                            ? "Найдены строки «Рис.»/«рис.»/«Рисунок» (положение относительно рисунка для PDF не проверяется)"
                            : "Подписи рисунков не найдены")
                    .expected("Под рисунком")
                    .actual(hasCaptions ? "Строки найдены" : "Не найдено")
                    .level(hasCaptions ? null : "warning")
                    .build());
        }
        if (figureCaptionExpectsAbove(figCap)) {
            boolean hasCaptions = !figureNumbers.isEmpty();
            results.add(CriteriaResultDto.builder()
                    .code("FIGURE_CAPTION_ABOVE")
                    .title("Подпись рисунка (над рисунком)")
                    .passed(hasCaptions)
                    .message(hasCaptions
                            ? "Строки «Рис.» найдены; порядок «подпись — рисунок» для PDF по тексту не проверяется"
                            : "Подписи рисунков не найдены")
                    .expected("Над рисунком")
                    .actual(hasCaptions ? "См. PDF_LAYOUT_LIMIT" : "Не найдено")
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
        boolean expectTitleAbovePdf = tableTitleExpectsAbove(tblPlacement);
        boolean expectTitleBelowPdf = tableTitleExpectsBelow(tblPlacement);
        if (tblPos != null && !tblPos.isBlank()) {
            results.add(CriteriaResultDto.builder()
                    .code("TABLE_POSITION")
                    .title("Позиция заголовка таблицы")
                    .passed(true)
                    .message("Для PDF выравнивание абзацев «Таблица …» по разметке не определяется")
                    .expected(tblPos.trim())
                    .actual("Только текстовый анализ")
                    .level("info")
                    .build());
        }
        if (tblTitle != null && !tblTitle.isBlank() && expectTitleAbovePdf) {
            results.add(CriteriaResultDto.builder()
                    .code("TABLE_AFTER_TITLE")
                    .title("Название таблицы над таблицей")
                    .passed(true)
                    .message("Для PDF порядок «заголовок — таблица» по структуре документа не определяется")
                    .expected("Над таблицей")
                    .actual("Только текстовый анализ")
                    .level("info")
                    .build());
        }
        if (tblTitle != null && !tblTitle.isBlank() && expectTitleBelowPdf) {
            results.add(CriteriaResultDto.builder()
                    .code("TABLE_TITLE_BELOW_TABLE")
                    .title("Название таблицы под таблицей")
                    .passed(true)
                    .message("Для PDF порядок «таблица — заголовок» по структуре документа не определяется")
                    .expected("Под таблицей")
                    .actual("Только текстовый анализ")
                    .level("info")
                    .build());
        }
        return results;
    }

    /** Проверка титульного листа по обязательным строкам (первая страница / начало документа). */
    public CriteriaResultDto checkTitlePageByRequiredStrings(String firstPageText, List<String> requiredStrings) {
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

    public int countPagesDocx(XWPFDocument doc) {
        int paragraphs = doc.getParagraphs().size();
        int tables = doc.getTables().stream().mapToInt(t -> t.getRows().size()).sum();
        int totalBlocks = paragraphs + tables;
        return Math.max(1, (int) Math.ceil(totalBlocks / 15.0));
    }

    public int countSourcesDocx(XWPFDocument doc) {
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

    public int countSourcesPdf(PDDocument doc) throws Exception {
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

    private static boolean shouldCheckTitlePage(TemplateCriteriaDto template) {
        if (template == null) {
            return false;
        }
        if (Boolean.TRUE.equals(template.getHasTitlePage())) {
            return true;
        }
        List<String> strings = template.getTitlePageRequiredStrings();
        return strings != null && strings.stream().anyMatch(s -> s != null && !s.isBlank());
    }

    public ValidationResultDto buildResult(List<CriteriaResultDto> results) {
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

    public CriteriaResultDto passed(String code, String title, String message) {
        return CriteriaResultDto.builder()
                .code(code)
                .title(title)
                .passed(true)
                .message(message)
                .build();
    }

    public CriteriaResultDto result(String code, String title, boolean ok, String expected, String actual) {
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

    public String getExtension(String filename) {
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

    public TemplateCriteriaDto defaultTemplate() {
        return TemplateCriteriaDto.builder()
                .fileFormat(".doc или .docx")
                .font("Times New Roman")
                .fontSize("14 пт")
                .minPages("10")
                .minSources("7")
                .illNumbering("Сквозная")
                .figurePosition("По центру страницы")
                .figureCaption("Под рисунком")
                .tableTitle("Слово «Таблица» + номер + наименование")
                .tablePosition("Справа")
                .tableTitlePlacement("Над таблицей")
                .hasToc(true)
                .hasIntroduction(true)
                .hasConclusion(true)
                .hasBibliography(true)
                .build();
    }

    /**
     * Если в JSON шаблона из БД нет блока про рисунки/таблицы, {@link #checkIllustrationsAndTablesDocx} сразу возвращает пустой список
     * и фронт не получает критерии FIGURE_* и TABLE_*. Подставляем те же значения, что и в {@link #defaultTemplate()}, не затирая уже заданные поля.
     */
    private TemplateCriteriaDto ensureIllustrationTableCriteria(TemplateCriteriaDto t) {
        if (!illustrationTableCriteriaAllBlank(t)) {
            return t;
        }
        TemplateCriteriaDto d = defaultTemplate();
        return t.toBuilder()
                .illNumbering(d.getIllNumbering())
                .figurePosition(d.getFigurePosition())
                .figureCaption(d.getFigureCaption())
                .tableTitle(d.getTableTitle())
                .tablePosition(d.getTablePosition())
                .tableTitlePlacement(d.getTableTitlePlacement())
                .build();
    }

    private static boolean illustrationTableCriteriaAllBlank(TemplateCriteriaDto t) {
        return isBlank(t.getIllNumbering()) && isBlank(t.getFigurePosition()) && isBlank(t.getFigureCaption())
                && isBlank(t.getTableTitle()) && isBlank(t.getTablePosition()) && isBlank(t.getTableTitlePlacement());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
