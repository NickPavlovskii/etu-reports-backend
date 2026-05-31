package com.etu.reports.service;

import com.etu.reports.dto.template.KeywordMatchDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TitlePageTemplateServiceKeywordTest {

    private final TitlePageTemplateService service = new TitlePageTemplateService();

    @Test
    void findKeywordMatches_findsLineWithContext() {
        String text = "Министерство науки\nкафедра информационных систем\n";
        List<KeywordMatchDto> matches = service.findKeywordMatches(text, "кафедра", 10);
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getText()).isEqualTo("кафедра");
        assertThat(matches.get(0).getLineNumber()).isEqualTo(2);
        assertThat(matches.get(0).getExcerpt()).contains("кафедра");
    }

    @Test
    void findKeywordMatches_emptyQueryReturnsEmpty() {
        assertThat(service.findKeywordMatches("text", "  ", 10)).isEmpty();
    }
}
