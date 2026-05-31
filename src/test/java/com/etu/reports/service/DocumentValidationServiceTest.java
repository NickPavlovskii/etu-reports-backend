package com.etu.reports.service;

import com.etu.reports.dto.template.TemplateCriteriaDto;
import com.etu.reports.dto.validation.BatchValidationResultDto;
import com.etu.reports.dto.validation.ValidationResultDto;
import com.etu.reports.service.validation.ValidationFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentValidationServiceTest {

    @Mock
    private ValidationFacade validationFacade;

    private DocumentValidationService service;

    @BeforeEach
    void setUp() {
        service = new DocumentValidationService(validationFacade, Runnable::run);
    }

    @Test
    void validateBatch_nullFilesTreatedAsEmpty() {
        BatchValidationResultDto out = service.validateBatch(
                null, null, false, 1, "2024-2025", "лаб", "контроль", "тема");
        assertThat(out.getError()).contains("Не переданы файлы");
        assertThat(out.getResults()).isEmpty();
    }

    @Test
    void validateBatch_tooManyFiles_returnsErrorWithoutCallingFacade() {
        List<MultipartFile> files = new ArrayList<>();
        for (int i = 0; i < DocumentValidationService.MAX_BATCH_FILES + 1; i++) {
            files.add(new MockMultipartFile("files", "f" + i + ".pdf", "application/pdf", new byte[]{1}));
        }
        BatchValidationResultDto out = service.validateBatch(
                files, null, false, null, null, null, null, null);
        assertThat(out.getError()).contains("максимум " + DocumentValidationService.MAX_BATCH_FILES);
        assertThat(out.getResults()).isEmpty();
    }

    @Test
    void validateBatch_oneFile_delegatesToFacade() throws Exception {
        when(validationFacade.validate(eq("doc.pdf"), any(), any(), eq(false)))
                .thenReturn(ValidationResultDto.builder().valid(true).percent(100).build());
        MockMultipartFile file = new MockMultipartFile("files", "doc.pdf", "application/pdf", "x".getBytes());
        BatchValidationResultDto out = service.validateBatch(
                Collections.singletonList(file),
                new TemplateCriteriaDto(),
                false,
                7,
                "2024-2025",
                "отчёт",
                "лаб",
                "тема 1");
        assertThat(out.getError()).isNull();
        assertThat(out.getResults()).hasSize(1);
        assertThat(out.getResults().get(0).getResult().isValid()).isTrue();
        assertThat(out.getAcademicYear()).isEqualTo("2024-2025");
        assertThat(out.getTemplateId()).isEqualTo(7);
        verify(validationFacade).validate(eq("doc.pdf"), any(), any(), eq(false));
    }

    @Test
    void validateBatch_emptyMultipartFile_returnsItemError() {
        MockMultipartFile empty = new MockMultipartFile("files", "empty.pdf", "application/pdf", new byte[0]);
        BatchValidationResultDto out = service.validateBatch(
                List.of(empty), null, false, null, null, null, null, null);
        assertThat(out.getResults()).hasSize(1);
        assertThat(out.getResults().get(0).getResult().getErrorMessage()).contains("не загружен");
    }

    @Test
    void validate_delegatesToFacade() {
        ValidationResultDto expected = ValidationResultDto.builder().valid(false).percent(40).build();
        when(validationFacade.validate(eq("a.docx"), any(), any(), eq(true))).thenReturn(expected);
        ValidationResultDto r = service.validate("a.docx", new byte[]{1, 2}, new TemplateCriteriaDto(), true);
        assertThat(r).isSameAs(expected);
        verify(validationFacade).validate(eq("a.docx"), any(), any(), eq(true));
    }
}
