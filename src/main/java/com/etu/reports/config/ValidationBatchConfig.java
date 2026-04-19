package com.etu.reports.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Пул потоков для параллельной проверки файлов в {@code /api/validate/batch}.
 * Ограничивает число одновременных тяжёлых задач (POI/PDFBox).
 */
@Configuration
public class ValidationBatchConfig {

    public static final String EXECUTOR_BEAN_NAME = "validationBatchExecutor";

    @Bean(name = EXECUTOR_BEAN_NAME)
    public Executor validationBatchExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(4);
        ex.setQueueCapacity(200);
        ex.setThreadNamePrefix("validation-batch-");
        ex.setWaitForTasksToCompleteOnShutdown(true);
        ex.setAwaitTerminationSeconds(60);
        ex.initialize();
        return ex;
    }
}
