package com.etu.reports.service.validation.parser;

import com.etu.reports.service.validation.model.DocumentContext;

/**
 * Парсинг бинарного документа в {@link DocumentContext}.
 */
public interface DocumentParser {

    /**
     * @param content содержимое файла
     * @return контекст для правил (текст, шрифты, страницы и т.д.)
     */
    DocumentContext parse(byte[] content) throws Exception;
}
