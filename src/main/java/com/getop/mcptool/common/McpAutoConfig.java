package com.getop.mcptool.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * MCP 工具自动配置
 * 提供 RestTemplate 和 ObjectMapper Bean
 */
@Configuration
public class McpAutoConfig {
    
    /**
     * 创建 RestTemplate Bean
     * 用于 HTTP 调用
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
    
    /**
     * 创建 ObjectMapper Bean
     * 用于 JSON 序列化和反序列化
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
