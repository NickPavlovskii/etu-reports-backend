package com.etu.reports.exception;

public class TemplateNotFoundException extends RuntimeException {

    public TemplateNotFoundException(int id) {
        super("Шаблон не найден: " + id);
    }
}
