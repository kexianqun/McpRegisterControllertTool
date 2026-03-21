package com.getop.mcptool.common.swagger;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Swagger/OpenAPI 解析器
 * 从 OpenAPI 规范中提取接口信息
 */
@Slf4j
@Component
public class SwaggerParser {
    
    private OpenAPI openAPI;
    
    /**
     * 设置 OpenAPI 对象
     */
    public void setOpenAPI(OpenAPI openAPI) {
        this.openAPI = openAPI;
        int pathCount = openAPI.getPaths() != null ? openAPI.getPaths().size() : 0;
        log.info("SwaggerParser 初始化完成，共 {} 个路径", pathCount);
        
        // 调试：打印所有路径
        if (openAPI.getPaths() != null) {
            log.debug("Swagger 路径列表:");
            for (String path : openAPI.getPaths().keySet()) {
                log.debug("  - {}", path);
            }
        }
    }
    
    /**
     * 获取接口描述
     * @param path 接口路径
     * @param method 请求方法
     * @return 接口描述
     */
    public String getOperationDescription(String path, String method) {
        if (openAPI == null || openAPI.getPaths() == null) {
            return null;
        }
        
        PathItem pathItem = openAPI.getPaths().get(path);
        if (pathItem == null) {
            log.debug("路径不存在：{}", path);
            return null;
        }
        
        Operation operation = getOperation(pathItem, method);
        if (operation == null) {
            log.debug("Operation 不存在：{} {}", method, path);
            return null;
        }
        
        // 优先使用 summary，其次使用 description
        if (operation.getSummary() != null && !operation.getSummary().isEmpty()) {
            return operation.getSummary();
        }
        
        return operation.getDescription();
    }
    
    /**
     * 获取参数 Schema
     * @param path 接口路径
     * @param method 请求方法
     * @return 参数 Schema
     */
    public Map<String, Object> getParametersSchema(String path, String method) {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        
        if (openAPI == null || openAPI.getPaths() == null) {
            log.debug("SwaggerParser: OpenAPI 或 Paths 为空");
            schema.put("properties", new HashMap<>());
            return schema;
        }
        
        PathItem pathItem = openAPI.getPaths().get(path);
        if (pathItem == null) {
            log.debug("SwaggerParser: 路径不存在：{}", path);
            schema.put("properties", new HashMap<>());
            return schema;
        }
        
        Operation operation = getOperation(pathItem, method);
        if (operation == null) {
            log.debug("SwaggerParser: Operation 不存在：{} {}", method, path);
            schema.put("properties", new HashMap<>());
            return schema;
        }
        
        Map<String, Object> properties = new HashMap<>();
        List<String> required = new ArrayList<>();
        
        // 1. 解析参数（包括 query parameters 和 path parameters）
        if (operation.getParameters() != null) {
            log.debug("SwaggerParser: 找到 {} 个参数", operation.getParameters().size());
            
            for (Parameter param : operation.getParameters()) {
                String paramName = param.getName();
                Map<String, Object> paramSchema = convertSchema(param.getSchema());
                
                // 添加参数描述
                if (param.getDescription() != null) {
                    paramSchema.put("description", param.getDescription());
                }
                
                // 添加示例值
                if (param.getExample() != null) {
                    paramSchema.put("example", param.getExample());
                }
                
                properties.put(paramName, paramSchema);
                log.debug("SwaggerParser: 参数 {} - 类型：{}", paramName, paramSchema.get("type"));
                
                // 检查是否必填
                if (param.getRequired() != null && param.getRequired()) {
                    required.add(paramName);
                }
            }
        } else {
            log.debug("SwaggerParser: 未找到参数列表");
        }
        
        // 2. 解析 RequestBody（如果有）
        if (operation.getRequestBody() != null && operation.getRequestBody().getContent() != null) {
            io.swagger.v3.oas.models.media.MediaType mediaType = 
                operation.getRequestBody().getContent().get("application/json");
            
            if (mediaType != null && mediaType.getSchema() != null) {
                log.debug("SwaggerParser: 找到 RequestBody Schema");
                
                Schema<?> bodySchema = mediaType.getSchema();
                
                // 处理对象类型的 RequestBody
                if ("object".equals(bodySchema.getType()) && bodySchema.getProperties() != null) {
                    log.debug("SwaggerParser: RequestBody 是对象类型，解析属性");
                    
                    for (Map.Entry<String, Schema> entry : bodySchema.getProperties().entrySet()) {
                        String propName = entry.getKey();
                        Map<String, Object> propSchema = convertSchema(entry.getValue());
                        properties.put(propName, propSchema);
                        log.debug("SwaggerParser: RequestBody 属性 {} - 类型：{}", propName, propSchema.get("type"));
                        
                        // 检查是否必填
                        if (bodySchema.getRequired() != null && bodySchema.getRequired().contains(propName)) {
                            required.add(propName);
                        }
                    }
                } 
                // 处理 $ref 引用类型
                else if (bodySchema.get$ref() != null) {
                    log.debug("SwaggerParser: RequestBody 是引用类型：{}", bodySchema.get$ref());
                    Map<String, Object> refSchema = resolveRef(bodySchema.get$ref());
                    
                    if (refSchema != null && refSchema.containsKey("properties")) {
                        Map<String, Object> refProperties = (Map<String, Object>) refSchema.get("properties");
                        for (Map.Entry<String, Object> entry : refProperties.entrySet()) {
                            properties.put(entry.getKey(), entry.getValue());
                            log.debug("SwaggerParser: 引用类型属性 {} - {}", entry.getKey(), entry.getValue());
                        }
                        
                        if (refSchema.containsKey("required")) {
                            required.addAll((List<String>) refSchema.get("required"));
                        }
                    }
                }
            }
        }
        
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        
        log.debug("SwaggerParser: 最终 Schema properties size: {}", properties.size());
        return schema;
    }
    
    /**
     * 获取响应 Schema
     * @param path 接口路径
     * @param method 请求方法
     * @return 响应 Schema
     */
    public Map<String, Object> getResponseSchema(String path, String method) {
        if (openAPI == null || openAPI.getPaths() == null) {
            return null;
        }
        
        PathItem pathItem = openAPI.getPaths().get(path);
        if (pathItem == null) {
            return null;
        }
        
        Operation operation = getOperation(pathItem, method);
        if (operation == null || operation.getResponses() == null) {
            return null;
        }
        
        // 获取 200 响应
        ApiResponse response = operation.getResponses().get("200");
        if (response == null) {
            response = operation.getResponses().get("default");
        }
        
        if (response == null || response.getContent() == null) {
            return null;
        }
        
        // 获取 JSON 响应
        io.swagger.v3.oas.models.media.MediaType mediaType = response.getContent().get("application/json");
        if (mediaType == null) {
            return null;
        }
        
        return convertSchema(mediaType.getSchema());
    }
    
    /**
     * 获取 Operation 对象
     */
    private Operation getOperation(PathItem pathItem, String method) {
        if (method == null) {
            return null;
        }
        
        switch (method.toUpperCase()) {
            case "GET": return pathItem.getGet();
            case "POST": return pathItem.getPost();
            case "PUT": return pathItem.getPut();
            case "DELETE": return pathItem.getDelete();
            case "PATCH": return pathItem.getPatch();
            default: return null;
        }
    }
    
    /**
     * 转换 Schema 对象为 Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> convertSchema(Schema<?> schema) {
        if (schema == null) {
            return new HashMap<>();
        }
        
        Map<String, Object> result = new HashMap<>();
        
        // 类型
        if (schema.getType() != null) {
            result.put("type", schema.getType());
        }
        
        // 描述
        if (schema.getDescription() != null) {
            result.put("description", schema.getDescription());
        }
        
        // 默认值
        if (schema.getDefault() != null) {
            result.put("default", schema.getDefault());
        }
        
        // 示例
        if (schema.getExample() != null) {
            result.put("example", schema.getExample());
        }
        
        // 枚举
        if (schema.getEnum() != null) {
            result.put("enum", schema.getEnum());
        }
        
        // 最小值/最大值
        if (schema.getMinimum() != null) {
            result.put("minimum", schema.getMinimum());
        }
        if (schema.getMaximum() != null) {
            result.put("maximum", schema.getMaximum());
        }
        
        // 数组类型
        if ("array".equals(schema.getType()) && schema.getItems() != null) {
            result.put("items", convertSchema(schema.getItems()));
        }
        
        // 对象类型（处理嵌套属性）
        if ("object".equals(schema.getType()) && schema.getProperties() != null) {
            Map<String, Object> properties = new HashMap<>();
            for (Map.Entry<String, Schema> entry : schema.getProperties().entrySet()) {
                properties.put(entry.getKey(), convertSchema(entry.getValue()));
            }
            result.put("properties", properties);
            
            // 必填字段
            if (schema.getRequired() != null) {
                result.put("required", schema.getRequired());
            }
        }
        
        // 引用类型（处理 $ref）
        if (schema.get$ref() != null) {
            result.put("$ref", schema.get$ref());
            // 解析引用
            Map<String, Object> refSchema = resolveRef(schema.get$ref());
            if (refSchema != null) {
                result.putAll(refSchema);
            }
        }
        
        return result;
    }
    
    /**
     * 解析 $ref 引用
     */
    private Map<String, Object> resolveRef(String ref) {
        if (openAPI == null || openAPI.getComponents() == null) {
            return null;
        }
        
        // 提取引用名称 #/components/schemas/User -> User
        String refName = ref.substring(ref.lastIndexOf('/') + 1);
        
        Schema<?> schema = openAPI.getComponents().getSchemas().get(refName);
        if (schema == null) {
            return null;
        }
        
        return convertSchema(schema);
    }
    
    /**
     * 检查路径是否存在
     */
    public boolean hasPath(String path) {
        return openAPI != null && openAPI.getPaths() != null && openAPI.getPaths().get(path) != null;
    }
    
    /**
     * 获取所有路径
     */
    public Set<String> getAllPaths() {
        if (openAPI == null || openAPI.getPaths() == null) {
            return Collections.emptySet();
        }
        return openAPI.getPaths().keySet();
    }
}
