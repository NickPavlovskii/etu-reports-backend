package com.etu.reports.service.validation;

import com.etu.reports.dto.validation.CriteriaResultDto;
import com.etu.reports.dto.validation.ValidationResultDto;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ValidationResultAssembler {

    private static final Map<String, Integer> WEIGHT_BY_CODE = defaultWeights();

    private static Map<String, Integer> defaultWeights() {
        Map<String, Integer> m = new HashMap<>();
        m.put("FILE_FORMAT", 5);
        m.put("FONT", 10);
        m.put("FONT_SIZE", 10);
        m.put("LINE_SPACING", 8);
        m.put("MIN_PAGES", 15);
        m.put("MIN_SOURCES", 12);
        m.put("HAS_TITLE_PAGE", 8);
        m.put("HAS_TOC", 6);
        m.put("HAS_INTRODUCTION", 6);
        m.put("HAS_MAIN_PART", 4);
        m.put("HAS_CONCLUSION", 6);
        m.put("HAS_BIBLIOGRAPHY", 10);
        m.put("HAS_APPENDICES", 4);
        m.put("ILL_NUMBERING", 4);
        m.put("ILL_NUMBERING_BY_SECTION", 4);
        m.put("FIGURE_POSITION", 4);
        m.put("FIGURE_CAPTION_POS", 4);
        m.put("FIGURE_CAPTION_ABOVE", 4);
        m.put("TABLE_TITLE", 4);
        m.put("TABLE_AFTER_TITLE", 3);
        m.put("TABLE_TITLE_BELOW_TABLE", 3);
        m.put("TABLE_POSITION", 3);
        m.put("TABLE_TITLE_TYPO", 3);
        m.put("FIGURE_CAPTION_TYPO", 3);
        m.put("PDF_LAYOUT_LIMIT", 0);
        return m;
    }

    public ValidationResultDto assembleEqual(List<CriteriaResultDto> results) {
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

    public ValidationResultDto assembleWeighted(List<CriteriaResultDto> results) {
        List<CriteriaResultDto> errors = results.stream()
                .filter(r -> !r.isPassed() && "error".equals(r.getLevel()))
                .collect(Collectors.toList());
        List<CriteriaResultDto> warnings = results.stream()
                .filter(r -> !r.isPassed() && ("warning".equals(r.getLevel()) || r.getLevel() == null))
                .collect(Collectors.toList());
        boolean valid = errors.isEmpty();
        int totalWeight = 0;
        int passedWeight = 0;
        for (CriteriaResultDto r : results) {
            int w = WEIGHT_BY_CODE.getOrDefault(r.getCode(), 5);
            totalWeight += w;
            if (r.isPassed()) {
                passedWeight += w;
            }
        }
        int percent = totalWeight == 0 ? 100 : (passedWeight * 100 / totalWeight);
        return ValidationResultDto.builder()
                .valid(valid)
                .percent(percent)
                .criteria(results)
                .errors(errors)
                .warnings(warnings)
                .build();
    }
}
