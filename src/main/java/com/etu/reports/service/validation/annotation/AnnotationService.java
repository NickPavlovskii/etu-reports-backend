package com.etu.reports.service.validation.annotation;

import com.etu.reports.dto.validation.ValidationResultDto;
import com.etu.reports.service.validation.internal.DocumentValidationDelegate;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.List;

/**
 * Выделение ошибок в DOCX (цвет прогона). PDF — сохранение копии после проверки (как в delegate).
 */
@Service
public class AnnotationService {

    private static final String HIGHLIGHT_COLOR = "FF0000";

    private final DocumentValidationDelegate delegate;

    public AnnotationService(DocumentValidationDelegate delegate) {
        this.delegate = delegate;
    }

    public ValidationResultDto annotateDocx(XWPFDocument doc, ValidationResultDto base,
                                            List<XWPFRun> runsToHighlight, String originalFilename) throws Exception {
        for (XWPFRun r : runsToHighlight) {
            try {
                r.setColor(HIGHLIGHT_COLOR);
            } catch (Exception ignored) {
            }
        }
        byte[] annotated = delegate.writeDocx(doc);
        String base64 = Base64.getEncoder().encodeToString(annotated);
        String outName = originalFilename.replaceFirst("(\\.docx?)$", "_замечания$1");
        if (outName.equals(originalFilename)) {
            outName = "document_замечания.docx";
        }
        return base.toBuilder()
                .annotatedFileBase64(base64)
                .annotatedFileName(outName)
                .build();
    }
}
