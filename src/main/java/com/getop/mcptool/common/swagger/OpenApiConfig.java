package com.getop.mcptool.common.swagger;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI Bean 配置
 * 确保 OpenAPI Bean 被创建
 */
@Configuration
public class OpenApiConfig {
    
    @Value("${spring.application.name:MCP Tool Service}")
    private String appName;
    
    @Value("${spring.application.version:1.0.0}")
    private String appVersion;
    
    /**
     * 创建 OpenAPI Bean
     * 如果 springdoc 没有自动创建，则手动创建
     */
    @Bean
    @ConditionalOnMissingBean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title(appName + " API")
                .version(appVersion)
                .description("MCP Tool Service API Documentation")
                .contact(new Contact()
                    .name("Support")
                    .email("support@example.com")));
    }
}
