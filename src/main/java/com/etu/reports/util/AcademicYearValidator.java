package com.etu.reports.util;

import java.time.LocalDate;

/**
 * Валидация формата учебного года: YYYY-YYYY (например 2024-2025).
 */
public final class AcademicYearValidator {

    private static final java.util.regex.Pattern PATTERN = java.util.regex.Pattern.compile("^\\d{4}-\\d{4}$");

    private AcademicYearValidator() {}

    /**
     * Учебный год, в который попадает дата (с 1 сентября по 31 августа): {@code start-(start+1)}.
     */
    public static String academicYearContaining(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("date не может быть null");
        }
        int y = date.getYear();
        int startYear = date.getMonthValue() >= 9 ? y : y - 1;
        return startYear + "-" + (startYear + 1);
    }

    /**
     * Проверяет и возвращает приведённое значение. Выбрасывает IllegalArgumentException при неверном формате.
     */
    public static String requireValid(String academicYear) {
        if (academicYear == null || academicYear.isBlank()) {
            throw new IllegalArgumentException("academicYear обязателен (формат: 2024-2025)");
        }
        String trimmed = academicYear.trim();
        if (!PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("academicYear должен быть в формате YYYY-YYYY (например 2024-2025), получено: " + academicYear);
        }
        return trimmed;
    }
}
