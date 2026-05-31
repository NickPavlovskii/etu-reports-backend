package com.etu.reports.service.validation.annotation;

import com.etu.reports.dto.validation.CriteriaResultDto;

/**
 * Заготовленные тексты комментариев Word для результатов автопроверки.
 */
public final class ValidationCommentScript {

    private static final String PREFIX = "[Автопроверка] ";

    private ValidationCommentScript() {
    }

    public static String forCriterion(CriteriaResultDto criterion) {
        if (criterion == null) {
            return PREFIX + "Замечание по оформлению документа.";
        }
        String code = criterion.getCode() != null ? criterion.getCode() : "";
        String title = nullToEmpty(criterion.getTitle());
        String message = nullToEmpty(criterion.getMessage());
        String expected = nullToEmpty(criterion.getExpected());
        String actual = nullToEmpty(criterion.getActual());

        return switch (code) {
            case "FONT" -> PREFIX + "Шрифт: " + message
                    + (expected.isBlank() ? "" : " Ожидалось: " + expected + ".")
                    + (actual.isBlank() ? "" : " Найдено: " + actual + ".");
            case "FONT_SIZE" -> PREFIX + "Размер шрифта: " + message
                    + (expected.isBlank() ? "" : " Требуется: " + expected + ".")
                    + (actual.isBlank() ? "" : " В документе: " + actual + ".");
            case "MIN_PAGES" -> PREFIX + "Объём: " + message
                    + (expected.isBlank() ? "" : " Минимум страниц: " + expected + ".")
                    + (actual.isBlank() ? "" : " В документе: " + actual + ".");
            case "MIN_SOURCES" -> PREFIX + "Список источников: " + message
                    + (expected.isBlank() ? "" : " Минимум записей: " + expected + ".")
                    + (actual.isBlank() ? "" : " Найдено: " + actual + ".");
            case "HAS_TITLE_PAGE" -> PREFIX + title + ": " + message
                    + (expected.isBlank() ? "" : " Обязательные фразы: " + expected + ".")
                    + (actual.isBlank() ? "" : " " + actual + ".");
            case "HAS_TOC", "HAS_INTRODUCTION", "HAS_CONCLUSION", "HAS_BIBLIOGRAPHY", "HAS_APPENDICES" ->
                    PREFIX + title + ": " + (message.isBlank() ? "раздел не найден." : message)
                            + (expected.isBlank() ? "" : " Ожидалось: «" + expected + "».");
            case "ILL_NUMBERING", "ILL_NUMBERING_BY_SECTION" -> PREFIX + "Нумерация иллюстраций: " + message;
            case "FIGURE_POSITION", "FIGURE_CAPTION_POS", "FIGURE_CAPTION_ABOVE" ->
                    PREFIX + "Рисунок: " + message + (expected.isBlank() ? "" : " (" + expected + ").");
            case "TABLE_TITLE", "TABLE_AFTER_TITLE", "TABLE_TITLE_BELOW_TABLE", "TABLE_POSITION",
                 "TABLE_TITLE_TYPO", "FIGURE_CAPTION_TYPO" ->
                    PREFIX + "Таблица / подпись: " + message + (expected.isBlank() ? "" : " (" + expected + ").");
            default -> PREFIX + (title.isBlank() ? "Замечание" : title) + ": "
                    + (message.isBlank() ? "не соответствует шаблону." : message);
        };
    }

    public static String forFormattingRun() {
        return PREFIX + "Проверьте шрифт или размер текста в этом фрагменте.";
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}
