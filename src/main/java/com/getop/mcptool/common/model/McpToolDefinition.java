package com.getop.mcptool.common.model;

import lombok.Data;
import lombok.Builder;

import java.util.ArrayList;
import java.util.Map;
import java.util.List;

/**
 * MCP 工具定义
 * 描述一个可被 MCP 调用的 HTTP 接口
 */
@Data
@Builder
public class McpToolDefinition {
    
    /**
     * 工具名称（符合 OpenAI 规范：^[a-zA-Z0-9_-]+$）
     * 例如：user_api_users_list, user_api_users_by_id
     */
    private String name;
    
    /**
     * 原始路径（用于内部映射）
     */
    @Builder.Default
    private String originalPath = "";
    
    /**
     * 工具描述（接口功能说明）
     */
    private String description;
    
    /**
     * 请求方式：GET, POST, PUT, DELETE
     */
    private String method;
    
    /**
     * 接口 URL（可能包含路径参数占位符，如 /api/users/{id}）
     */
    private String url;
    
    /**
     * 输入参数 Schema（用于 MCP 协议）
     */
    private Map<String, Object> inputSchema;
    
    /**
     * 路径参数列表（如 ["userId"]）
     */
    @Builder.Default
    private List<String> pathVariables = List.of();
    
    /**
     * 查询参数列表（@RequestParam）
     */
    @Builder.Default
    private List<String> queryParams = List.of();
    
    /**
     * 请求体参数（@RequestBody）
     */
    @Builder.Default
    private List<String> bodyParams = List.of();
    
    /**
     * 是否需要认证
     */
    private boolean requiresAuth;
    
    /**
     * 认证类型：Bearer, Basic, ApiKey, Custom
     */
    @Builder.Default
    private String authType = "Bearer";
    
    /**
     * Token 来源：param, config(从配置文件中取)
     */
    @Builder.Default
    private String tokenSource = "param";
    
    /**
     * Token 配置键名（从配置文件中读取）
     */
    @Builder.Default
    private String tokenConfigKey = "token";
    
    /**
     * 自定义请求头列表
     */
    @Builder.Default
    private List<CustomHeader> customHeaders = new ArrayList<>();
    
    /**
     * 自定义请求头
     */
    @lombok.Data
    @lombok.Builder
    public static class CustomHeader {
        private String name;
        private String value;
        private String source; // config, param, fixed
    }
}
