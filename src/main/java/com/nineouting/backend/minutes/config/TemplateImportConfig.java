package com.nineouting.backend.minutes.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class TemplateImportConfig {

    /** REQ-195 양식 가져오기를 요청과 따로 처리한다. 가득 차면 접수하지 않는다 (TEMPLATE_IMPORT_UNAVAILABLE) */
    @Bean(name = "templateImportExecutor", destroyMethod = "shutdown")
    ThreadPoolTaskExecutor templateImportExecutor(TemplateImportProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.workers());
        executor.setMaxPoolSize(properties.workers());
        executor.setQueueCapacity(properties.workers() * 5);
        executor.setThreadNamePrefix("template-import-");
        executor.initialize();
        return executor;
    }
}
