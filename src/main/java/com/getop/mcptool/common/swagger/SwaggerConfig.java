package com.getop.mcptool.common.swagger;

import io.swagger.v3.oas.models.OpenAPI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import jakarta.annotation.PostConstruct;

/**
 * Swagger/OpenAPI 配置
 * 从 Spring 容器获取 OpenAPI Bean 并注入到 SwaggerParser
 */
@Slf4j
@Configuration
@ConditionalOnClass(name = "io.swagger.v3.oas.models.OpenAPI")
@ConditionalOnProperty(name = "mcp.swagger.enabled", havingValue = "true", matchIfMissing = true)
@DependsOn("openApiConfig")  // 确保 OpenApiConfig 先初始化
public class SwaggerConfig {
    
    private final SwaggerParser swaggerParser;
    
    @Autowired(required = false)
    private OpenAPI openAPI;
    
    public SwaggerConfig(SwaggerParser swaggerParser) {
        this.swaggerParser = swaggerParser;
    }
    
    /**
     * 在 Bean 初始化后注入 OpenAPI
     */
    @PostConstruct
    public void init() {
        // 延迟一点时间，等待 springdoc 完全初始化
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        if (openAPI != null) {
            swaggerParser.setOpenAPI(openAPI);
            int pathCount = openAPI.getPaths() != null ? openAPI.getPaths().size() : 0;
            log.info("✅ Swagger 集成已启用，共 {} 个接口", pathCount);
            
            // 打印所有路径
            if (openAPI.getPaths() != null) {
                log.debug("Swagger 路径列表:");
                for (String path : openAPI.getPaths().keySet()) {
                    log.debug("  - {}", path);
                }
            }
        } else {
            log.warn("⚠️ 未找到 OpenAPI Bean，Swagger 集成将不可用。请确保：\n" +
                    "  1. 已添加 springdoc-openapi-starter-webmvc-ui 依赖\n" +
                    "  2. springdoc.api-docs.enabled=true\n" +
                    "  3. 访问过 /v3/api-docs 触发 OpenAPI Bean 创建");
        }
    }
}
