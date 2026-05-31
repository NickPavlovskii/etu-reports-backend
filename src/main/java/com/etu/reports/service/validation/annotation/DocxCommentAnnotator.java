package com.etu.reports.service.validation.annotation;

import com.etu.reports.dto.validation.CriteriaResultDto;
import org.apache.poi.xwpf.usermodel.XWPFComments;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.xmlbeans.XmlCursor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTComment;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTComments;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTMarkupRange;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Добавляет в DOCX комментарии Word с текстами {@link ValidationCommentScript}.
 */
@Component
public class DocxCommentAnnotator {

    private static final String AUTHOR = "Автопроверка";
    private static final String INITIALS = "АП";

    public void annotate(XWPFDocument doc, List<CriteriaResultDto> failed, List<XWPFRun> highlightedRuns) {
        if (doc == null || failed == null || failed.isEmpty()) {
            return;
        }
        XWPFComments commentsDocument = doc.getDocComments();
        if (commentsDocument == null) {
            commentsDocument = doc.createComments();
        }
        CTComments comments = commentsDocument.getCtComments();
        Set<XWPFRun> commentedRuns = new HashSet<>();

        for (XWPFRun run : highlightedRuns != null ? highlightedRuns : List.<XWPFRun>of()) {
            if (run == null || commentedRuns.contains(run)) {
                continue;
            }
            commentTextRun(run, comments, ValidationCommentScript.forFormattingRun());
            commentedRuns.add(run);
        }

        for (CriteriaResultDto criterion : failed) {
            if (criterion == null || criterion.isPassed()) {
                continue;
            }
            String text = ValidationCommentScript.forCriterion(criterion);
            XWPFParagraph anchor = findAnchorParagraph(doc, criterion);
            if (anchor != null && !anchor.getRuns().isEmpty()) {
                XWPFRun run = anchor.getRuns().get(0);
                if (!commentedRuns.contains(run)) {
                    commentTextRun(run, comments, text);
                    commentedRuns.add(run);
                }
            } else if (!doc.getParagraphs().isEmpty()) {
                XWPFParagraph first = doc.getParagraphs().get(0);
                if (!first.getRuns().isEmpty()) {
                    XWPFRun run = first.getRuns().get(0);
                    if (!commentedRuns.contains(run)) {
                        commentTextRun(run, comments, text);
                        commentedRuns.add(run);
                    }
                }
            }
        }
    }

    private XWPFParagraph findAnchorParagraph(XWPFDocument doc, CriteriaResultDto criterion) {
        String code = criterion.getCode() != null ? criterion.getCode() : "";
        return switch (code) {
            case "HAS_TOC" -> findParagraphContaining(doc, "содержание", "оглавление");
            case "HAS_INTRODUCTION" -> findParagraphContaining(doc, "введение");
            case "HAS_CONCLUSION" -> findParagraphContaining(doc, "заключение");
            case "HAS_BIBLIOGRAPHY" -> findParagraphContaining(doc, "список литературы", "список использованных источников", "библиограф");
            case "HAS_APPENDICES" -> findParagraphContaining(doc, "приложени");
            case "HAS_TITLE_PAGE" -> doc.getParagraphs().isEmpty() ? null : doc.getParagraphs().get(0);
            case "FIGURE_POSITION", "FIGURE_CAPTION_POS", "FIGURE_CAPTION_ABOVE", "FIGURE_CAPTION_TYPO" ->
                    findParagraphContaining(doc, "рис.", "рисунок", "figure");
            case "TABLE_TITLE", "TABLE_AFTER_TITLE", "TABLE_TITLE_BELOW_TABLE", "TABLE_POSITION", "TABLE_TITLE_TYPO" ->
                    findParagraphContaining(doc, "таблица", "table");
            default -> null;
        };
    }

    private XWPFParagraph findParagraphContaining(XWPFDocument doc, String... needles) {
        for (XWPFParagraph p : doc.getParagraphs()) {
            String t = p.getText();
            if (t == null || t.isBlank()) {
                continue;
            }
            String lower = t.toLowerCase(Locale.ROOT);
            for (String needle : needles) {
                if (lower.contains(needle.toLowerCase(Locale.ROOT))) {
                    return p;
                }
            }
        }
        return null;
    }

    private void commentTextRun(XWPFRun run, CTComments comments, String commentText) {
        if (run == null || commentText == null || commentText.isBlank()) {
            return;
        }
        BigInteger cId = nextCommentId(comments);
        CTComment ctComment = comments.addNewComment();
        ctComment.setId(cId);
        ctComment.setAuthor(AUTHOR);
        ctComment.setInitials(INITIALS);
        ctComment.setDate(new GregorianCalendar(Locale.US));
        ctComment.addNewP().addNewR().addNewT().setStringValue(commentText);

        CTMarkupRange commentRangeStart = insertCommentRangeStartBefore(run);
        commentRangeStart.setId(cId);
        CTMarkupRange commentRangeEnd = insertCommentRangeEndAfter(run);
        commentRangeEnd.setId(cId);
        insertCommentReferenceAfter(commentRangeEnd, cId);
    }

    private static BigInteger nextCommentId(CTComments comments) {
        BigInteger cId = BigInteger.ZERO;
        for (CTComment ctComment : comments.getCommentList()) {
            if (ctComment.getId().compareTo(cId) > 0) {
                cId = ctComment.getId();
            }
        }
        return cId.add(BigInteger.ONE);
    }

    private static CTMarkupRange insertCommentRangeStartBefore(XWPFRun run) {
        String uri = CTMarkupRange.type.getName().getNamespaceURI();
        XmlCursor cursor = run.getCTR().newCursor();
        cursor.beginElement("commentRangeStart", uri);
        cursor.toParent();
        return (CTMarkupRange) cursor.getObject();
    }

    private static CTMarkupRange insertCommentRangeEndAfter(XWPFRun run) {
        String uri = CTMarkupRange.type.getName().getNamespaceURI();
        XmlCursor cursor = run.getCTR().newCursor();
        cursor.toEndToken();
        cursor.toNextToken();
        cursor.beginElement("commentRangeEnd", uri);
        cursor.toParent();
        return (CTMarkupRange) cursor.getObject();
    }

    private static void insertCommentReferenceAfter(CTMarkupRange commentRangeEnd, BigInteger cId) {
        String uri = CTR.type.getName().getNamespaceURI();
        XmlCursor cursor = commentRangeEnd.newCursor();
        cursor.toEndToken();
        cursor.toNextToken();
        cursor.beginElement("r", uri);
        cursor.toParent();
        CTR ctr = (CTR) cursor.getObject();
        ctr.addNewCommentReference().setId(cId);
    }
}
