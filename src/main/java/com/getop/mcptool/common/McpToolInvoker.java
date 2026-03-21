package com.getop.mcptool.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getop.mcptool.common.auth.TokenManager;
import com.getop.mcptool.common.model.McpToolDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具调用器
 * 负责实际调用 HTTP 接口
 */
@Slf4j
@Component
public class McpToolInvoker {
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final McpToolRegistry toolRegistry;
    private final TokenManager tokenManager;
    
    public McpToolInvoker(RestTemplate restTemplate, 
                         ObjectMapper objectMapper,
                         McpToolRegistry toolRegistry,
                         TokenManager tokenManager) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.toolRegistry = toolRegistry;
        this.tokenManager = tokenManager;
    }
    
    /**
     * 调用工具
     * @param toolName 工具名称
     * @param arguments 调用参数
     * @return 接口响应结果
     */
    public Object invoke(String toolName, Map<String, Object> arguments) {
        log.info("调用 MCP 工具：{}, 参数：{}", toolName, arguments.toString());
        
        McpToolDefinition tool = toolRegistry.getTool(toolName);
        if (tool == null) {
            throw new RuntimeException("工具不存在：" + toolName);
        }
        
        try {
            // 构建请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // 添加认证头（如果需要）
            addAuthHeaders(headers, tool, arguments);
            
            // 1. 替换路径参数，构建 URL
            String url = buildUrlWithPathVariable(tool.getUrl(), arguments, tool.getPathVariables());
            
            // 2. 处理查询参数（@RequestParam）
            if (!tool.getQueryParams().isEmpty()) {
                url = appendQueryParams(url, arguments, tool.getQueryParams());
            } else if ("GET".equalsIgnoreCase(tool.getMethod())) {
                // GET 请求但没有明确标注 query params，附加所有参数（向后兼容）
                url = appendQueryParams(url, arguments, tool.getPathVariables());
            }
            
            // 3. 创建请求实体（处理 @RequestBody）
            // 从 inputSchema.properties 获取所有参数名
            List<String> allParams = new ArrayList<>();
            if (tool.getInputSchema() != null && tool.getInputSchema().containsKey("properties")) {
                Map<String, Object> properties = (Map<String, Object>) tool.getInputSchema().get("properties");
                allParams.addAll(properties.keySet());
            }
            HttpEntity<?> requestEntity = createRequestEntity(
                arguments, 
                tool.getMethod(), 
                headers,
                tool.getBodyParams(),
                allParams
            );
            log.info("调用接口：{} {}", tool.getMethod(), url);
            log.debug("请求体：{}", requestEntity.getBody());
            
            // 4. 调用接口
            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.valueOf(tool.getMethod()),
                requestEntity,
                String.class
            );
            
            log.info("工具调用结果：{}", response.getBody());
            
            // 5. 解析响应
            return objectMapper.readValue(response.getBody(), Map.class);
            
        } catch (Exception e) {
            log.error("工具调用失败：{}", toolName, e);
            throw new RuntimeException("工具调用失败：" + e.getMessage(), e);
        }
    }
    
    /**
     * 添加认证请求头
     * @param headers 请求头
     * @param tool 工具定义
     * @param arguments 调用参数
     */
    private void addAuthHeaders(HttpHeaders headers, McpToolDefinition tool, Map<String, Object> arguments) {
        if (!tool.isRequiresAuth()) {
            return;
        }
        
        log.debug("工具需要认证：{}, 认证类型：{}", tool.getName(), tool.getAuthType());
        // 1. Bearer Token 认证
        if ("Bearer".equalsIgnoreCase(tool.getAuthType())) {
            String token = getTokenValue(tool, arguments);
            if (token != null) {
                headers.setBearerAuth(token);
                log.debug("添加 Bearer Token: {}", maskToken(token));
            } else {
                log.warn("需要 Bearer Token 但未找到");
            }
        }
        // 2. Basic 认证
        else if ("Basic".equalsIgnoreCase(tool.getAuthType())) {
            String token = getTokenValue(tool, arguments);
            if (token != null) {
                headers.setBasicAuth(token);
                log.debug("添加 Basic Auth");
            }
        }
        // 3. API Key 认证
        else if ("ApiKey".equalsIgnoreCase(tool.getAuthType())) {
            String token = getTokenValue(tool, arguments);
            if (token != null) {
                headers.set("X-API-Key", token);
                log.debug("添加 API Key: {}", maskToken(token));
            }
        }
        // 4. 自定义认证
        else if ("Custom".equalsIgnoreCase(tool.getAuthType())) {
            // 添加自定义请求头
            if (tool.getCustomHeaders() != null) {
                for (McpToolDefinition.CustomHeader header : tool.getCustomHeaders()) {
                    String value = header.getValue();
                    if ("config".equals(header.getSource())) {
                        value = tokenManager.getToken(value);
                    }
                    if (value != null) {
                        headers.set(header.getName(), value);
                        log.debug("添加自定义头：{} = {}", header.getName(), maskToken(value));
                    }
                }
            }
        }
    }
    
    /**
     * 获取 Token 值
     */
    private String getTokenValue(McpToolDefinition tool, Map<String, Object> arguments) {
        // 1. 从参数中获取
        if ("param".equals(tool.getTokenSource()) && arguments.containsKey("token")) {
            return arguments.get("token").toString();
        }
        
        // 2. 从配置中获取
        if ("config".equals(tool.getTokenSource()) && tool.getTokenConfigKey() != null) {
            return tokenManager.getToken(tool.getTokenConfigKey());
        }
        
        // 3. 默认从 header 获取（使用默认 Token）
        return tokenManager.getToken("token");
    }
    
    /**
     * 掩码 Token
     */
    private String maskToken(String token) {
        if (token == null || token.length() < 8) {
            return "***";
        }
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }
    
    /**
     * 替换路径参数
     * 如：/api/users/{id} + {id: 1} -> /api/users/1
     * @param url 包含占位符的 URL
     * @param args 参数
     * @param pathVariables 路径参数列表
     * @return 替换后的 URL
     */
    private String buildUrlWithPathVariable(String url, Map<String, Object> args, List<String> pathVariables) {
        String result = url;
        
        if (pathVariables != null && !pathVariables.isEmpty()) {
            for (String pathVar : pathVariables) {
                if (args != null && args.containsKey(pathVar)) {
                    Object value = args.get(pathVar);
                    // 替换 {xxx} 为实际值
                    result = result.replace("{" + pathVar + "}", value != null ? value.toString() : "");
                }
            }
        }
        
        log.debug("路径参数替换：{} -> {}", url, result);
        return result;
    }
    
    /**
     * 附加查询参数到 URL
     * @param url 基础 URL
     * @param args 所有参数
     * @param excludeParams 需要排除的参数列表（路径参数 + 其他不需要附加的）
     * @return 带查询参数的 URL
     */
    private String appendQueryParams(String url, Map<String, Object> args, List<String> excludeParams) {
        if (args == null || args.isEmpty()) {
            return url;
        }
        
        StringBuilder sb = new StringBuilder(url);
        boolean hasQuery = false;
        
        for (Map.Entry<String, Object> entry : args.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            // 跳过排除的参数
            if (excludeParams != null && excludeParams.contains(key)) {
                continue;
            }
            
            if (!hasQuery) {
                sb.append(url.contains("?") ? "&" : "?");
                hasQuery = true;
            } else {
                sb.append("&");
            }
            
            String encodedValue = URLEncoder.encode(
                value != null ? value.toString() : "", 
                StandardCharsets.UTF_8
            );
            sb.append(key).append("=").append(encodedValue);
        }
        
        return sb.toString();
    }
    
    /**
     * 创建请求实体（处理 @RequestBody）
     * @param args 所有参数
     * @param method 请求方式
     * @param headers 请求头
     * @param bodyParams 请求体参数列表
     * @param allParams 所有参数名列表（用于判断哪些是 body 属性）
     * @return 请求实体
     */
    private HttpEntity<?> createRequestEntity(Map<String, Object> args, 
                                             String method, 
                                             HttpHeaders headers,
                                             List<String> bodyParams,
                                             List<String> allParams) {
        if ("GET".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
            return new HttpEntity<>(headers);
        }
        
        // 如果有 @RequestBody 参数
        if (bodyParams != null && !bodyParams.isEmpty()) {
            Map<String, Object> body = new HashMap<>();
            
            for (String bodyParam : bodyParams) {
                if ("body".equals(bodyParam) && args.containsKey("body")) {
                    // body 是整个对象（嵌套结构）
                    body = (Map<String, Object>) args.get("body");
                    break;
                }
            }
            
            // 如果 body 为空，说明是扁平化参数（从 Swagger 解析的 @RequestBody 属性）
            if (body.isEmpty() && allParams != null) {
                for (String param : allParams) {
                    // 排除路径参数和查询参数，剩下的就是 body 属性
                    if (args.containsKey(param)) {
                        body.put(param, args.get(param));
                    }
                }
            }
            
            if (!body.isEmpty()) {
                log.debug("请求体：{}", body);
                return new HttpEntity<>(body, headers);
            }
        }
        
        // 向后兼容：如果没有 bodyParams，使用所有参数
        return new HttpEntity<>(args, headers);
    }
}
