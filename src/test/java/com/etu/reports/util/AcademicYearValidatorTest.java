package com.etu.reports.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AcademicYearValidatorTest {

    @Test
    void requireValid_acceptsTrimmedFormat() {
        assertThat(AcademicYearValidator.requireValid("  2024-2025  ")).isEqualTo("2024-2025");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "2024", "24-25", "2024/2025", "abcd-abcd"})
    void requireValid_rejectsInvalid(String invalid) {
        assertThatThrownBy(() -> AcademicYearValidator.requireValid(invalid))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({
            "2024-09-01, 2024-2025",
            "2024-08-31, 2023-2024",
            "2025-01-15, 2024-2025"
    })
    void academicYearContaining(LocalDate date, String expected) {
        assertThat(AcademicYearValidator.academicYearContaining(date)).isEqualTo(expected);
    }

    @Test
    void academicYearContaining_nullThrows() {
        assertThatThrownBy(() -> AcademicYearValidator.academicYearContaining(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
