package com.etu.reports.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Конфигурация приложения. Значения из application.properties (app.*).
 */
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** Кафедра для фильтрации (например, "каф.ИС"). */
    private String departmentFilter = "каф.ИС";

    /** Директория хранения файлов отчётов. */
    private String reportsStorageDir = "reports_storage";

    public String getDepartmentFilter() {
        return departmentFilter;
    }

    public void setDepartmentFilter(String departmentFilter) {
        this.departmentFilter = departmentFilter;
    }

    public String getReportsStorageDir() {
        return reportsStorageDir;
    }

    public void setReportsStorageDir(String reportsStorageDir) {
        this.reportsStorageDir = reportsStorageDir;
    }
}
