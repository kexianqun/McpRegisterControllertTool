package com.getop.mcptool.common.annotation;

import java.lang.annotation.*;

/**
 * MCP 工具注解
 * 标记在 Controller 方法上，表示该方法可以被 MCP 调用
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpTool {
    
    /**
     * 工具名称（可选，默认使用方法名）
     */
    String name() default "";
    
    /**
     * 工具描述（可选，后期从 Swagger 获取）
     */
    String description() default "";
    
    /**
     * 是否需要认证
     */
    boolean requiresAuth() default false;
    
    /**
     * 认证类型：Bearer, Basic, ApiKey, Custom
     */
    String authType() default "Bearer";
    
    /**
     * Token 来源： param, config
     */
    String tokenSource() default "param";
    
    /**
     * Token 配置键名
     */
    String tokenConfigKey() default "token";
}
