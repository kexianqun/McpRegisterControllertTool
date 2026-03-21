package com.getop.mcptool.common;

import com.getop.mcptool.common.annotation.McpTool;
import com.getop.mcptool.common.model.McpToolDefinition;
import com.getop.mcptool.common.swagger.SwaggerParser;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

/**
 * MCP 工具扫描器（增强版 - 集成 Swagger）
 * 自动扫描 Controller 中的接口并注册为 MCP 工具
 * 支持从 Swagger 获取接口描述和详细参数 Schema
 */
@Slf4j
@Component
public class McpToolScanner implements BeanPostProcessor {

    private final McpToolRegistry toolRegistry;
    private final SwaggerParser swaggerParser;

    /**
     * 服务基础 URL（从配置读取）
     */
    @Value("${mcp.service.base-url:http://127.0.0.1:${server.port:8080}}")
    private String serviceBaseUrl;

    public McpToolScanner(McpToolRegistry toolRegistry, SwaggerParser swaggerParser) {
        this.toolRegistry = toolRegistry;
        this.swaggerParser = swaggerParser;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> targetClass = AopProxyUtils.ultimateTargetClass(bean);
        // 检查是否是 Controller
        if (isController(targetClass)) {
            scanAndRegister(bean, targetClass);
        }
        return bean;
    }

    /**
     * 判断是否是 Controller
     */
    private boolean isController(Class<?>  clazz) {
        return AnnotationUtils.findAnnotation(clazz, RestController.class) != null ||
                AnnotationUtils.findAnnotation(clazz, Controller.class) != null;
    }

    /**
     * 扫描并注册 Controller 中的接口
     */
    private void scanAndRegister(Object bean, Class<?> clazz) {
        // 获取类级别的请求路径
        String basePath = getBasePath(clazz);

        log.info("扫描 Controller: {}.{}. 基础路径：{}", clazz.getPackage().getName(), clazz.getSimpleName(), basePath);
        // 扫描所有公共方法
        int count = 0;
        for (Method method : clazz.getMethods()) {
            // 检查是否有 @McpTool 注解
            McpTool mcpTool = method.getAnnotation(McpTool.class);
            if (mcpTool != null) {
                registerTool(bean, clazz, method, basePath, mcpTool);
                count++;
            }
        }

        if (count > 0) {
            log.info("Controller: {} 注册了 {} 个 MCP 工具", clazz.getSimpleName(), count);
        }
    }

    /**
     * 获取 Controller 的基础路径
     */
    private String getBasePath(Class<?> clazz) {
        RequestMapping requestMapping = AnnotationUtils.findAnnotation(clazz, RequestMapping.class);
        if (requestMapping != null && requestMapping.path().length > 0) {
            return requestMapping.path()[0];
        }
        return "";
    }

    /**
     * 注册工具
     */
    private void registerTool(Object bean, Class<?> clazz, Method method,
                              String basePath, McpTool mcpTool) {
        // 获取请求路径
        String path = getMethodPath(method, basePath);

        // 获取请求方式
        String httpMethod = getHttpMethod(method);

        // 构建工具名称（使用完整路径）
        String toolName = buildToolName(clazz, path);

        // 构建工具描述（优先从 Swagger 获取）
        String description = buildDescription(method, mcpTool, path, httpMethod);

        // 构建输入 Schema（优先从 Swagger 获取）
        Map<String, Object> inputSchema = buildInputSchema(method, path, httpMethod);

        // 提取不同类型的参数
        List<String> pathVariables = extractPathVariables(path);
        ParameterTypes paramTypes = extractParameterTypes(method);

        // 创建工具定义
        McpToolDefinition tool = McpToolDefinition.builder()
                .name(toolName)
                .originalPath(path)  // 保存原始路径
                .description(description)
                .method(httpMethod)
                .url(serviceBaseUrl + path)
                .inputSchema(inputSchema)
                .pathVariables(pathVariables)
                .queryParams(paramTypes.queryParams)
                .bodyParams(paramTypes.bodyParams)
                .requiresAuth(mcpTool.requiresAuth())
                .tokenSource(mcpTool.tokenSource())
                .authType(mcpTool.authType())
                .tokenSource(mcpTool.tokenSource())
                .tokenConfigKey(mcpTool.tokenConfigKey())
                .build();

        // 注册工具
        toolRegistry.register(tool);
    }

    /**
     * 获取方法的请求路径
     */
    private String getMethodPath(Method method, String basePath) {
        // 检查各种请求映射注解
        RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
        if (requestMapping != null && requestMapping.value().length > 0) {
            return basePath + requestMapping.value()[0];
        }

        GetMapping getMapping = method.getAnnotation(GetMapping.class);
        if (getMapping != null && getMapping.value().length > 0) {
            return basePath + getMapping.value()[0];
        }

        PostMapping postMapping = method.getAnnotation(PostMapping.class);
        if (postMapping != null && postMapping.value().length > 0) {
            return basePath + postMapping.value()[0];
        }

        PutMapping putMapping = method.getAnnotation(PutMapping.class);
        if (putMapping != null && putMapping.value().length > 0) {
            return basePath + putMapping.value()[0];
        }

        DeleteMapping deleteMapping = method.getAnnotation(DeleteMapping.class);
        if (deleteMapping != null && deleteMapping.value().length > 0) {
            return basePath + deleteMapping.value()[0];
        }

        return basePath;
    }

    /**
     * 提取路径中的路径参数（如 /api/users/{id} -> ["id"]）
     */
    private List<String> extractPathVariables(String path) {
        List<String> variables = new ArrayList<>();
        if (path != null && path.contains("{")) {
            // 使用正则提取 {xxx} 中的内容
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\{([^}]+)\\}");
            java.util.regex.Matcher matcher = pattern.matcher(path);
            while (matcher.find()) {
                variables.add(matcher.group(1));
            }
        }
        return variables;
    }

    /**
     * 参数类型分类
     */
    @lombok.Data
    @lombok.Builder
    private static class ParameterTypes {
        private List<String> queryParams;   // @RequestParam
        private List<String> bodyParams;    // @RequestBody
        private List<String> pathVariables; // @PathVariable
    }

    /**
     * 提取不同类型的参数
     * 注：@RequestBody 的属性已经从 Swagger Schema 中解析到 inputSchema.properties
     */
    private ParameterTypes extractParameterTypes(Method method) {
        List<String> queryParams = new ArrayList<>();
        List<String> bodyParams = new ArrayList<>();
        List<String> pathVariables = new ArrayList<>();

        for (Parameter param : method.getParameters()) {
            if (param.isAnnotationPresent(RequestParam.class)) {
                RequestParam rp = param.getAnnotation(RequestParam.class);
                queryParams.add(rp.value().isEmpty() ? param.getName() : rp.value());
            } else if (param.isAnnotationPresent(RequestBody.class)) {
                // @RequestBody 参数，标记为 body，实际属性从 Swagger 获取
                bodyParams.add("body");
            } else if (param.isAnnotationPresent(PathVariable.class)) {
                PathVariable pv = param.getAnnotation(PathVariable.class);
                pathVariables.add(pv.value().isEmpty() ? param.getName() : pv.value());
            }
        }

        log.debug("参数类型提取：query={}, body={}, path={}",
                queryParams, bodyParams, pathVariables);

        return ParameterTypes.builder()
                .queryParams(queryParams)
                .bodyParams(bodyParams)
                .pathVariables(pathVariables)
                .build();
    }

    /**
     * 获取请求方式
     */
    private String getHttpMethod(Method method) {
        if (method.getAnnotation(GetMapping.class) != null) {
            return "GET";
        }
        if (method.getAnnotation(PostMapping.class) != null) {
            return "POST";
        }
        if (method.getAnnotation(PutMapping.class) != null) {
            return "PUT";
        }
        if (method.getAnnotation(DeleteMapping.class) != null) {
            return "DELETE";
        }

        RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
        if (requestMapping != null && requestMapping.method().length > 0) {
            return requestMapping.method()[0].name();
        }

        return "GET"; // 默认 GET
    }

    /**
     * 构建工具名称（符合 OpenAI 规范：^[a-zA-Z0-9_-]+$）
     * 将路径转换为合法的标识符格式
     */
    private String buildToolName(Class<?> clazz, String path) {
        // 使用类名 + 路径作为工具名称
        String simpleName = clazz.getSimpleName().replace("Controller", "").toLowerCase();

        // 构建原始名称
        String rawName = simpleName + "_" + path;

        // 规范化：转换为符合 OpenAI 规范的名称
        String normalizedName = normalizeToolName(rawName);

        log.debug("工具名称规范化：{} -> {}", rawName, normalizedName);
        return normalizedName;
    }

    /**
     * 规范化工具名称
     * 规则：只保留字母、数字、下划线、中划线
     * 将 / 替换为 _，将 {xxx} 替换为 _xxx_
     */
    private String normalizeToolName(String name) {
        String result = name;

        // 1. 移除开头的 /
        if (result.startsWith("/")) {
            result = result.substring(1);
        }

        // 2. 将 / 替换为 _
        result = result.replace("/", "_");

        // 3. 将 {xxx} 替换为 _xxx_
        result = result.replaceAll("\\{([^}]+)\\}", "_$1_");

        // 4. 将连续的多个 _ 合并为一个
        result = result.replaceAll("_+", "_");

        // 5. 移除开头和结尾的 _
        result = result.replaceAll("^_|_$", "");

        // 6. 确保只包含合法字符（防御性处理）
        result = result.replaceAll("[^a-zA-Z0-9_-]", "");

        // 7. 如果为空，使用默认名称
        if (result.isEmpty()) {
            result = "unknown_tool";
        }

        return result.toLowerCase();
    }

    /**
     * 构建工具描述（优先从 Swagger 获取）
     */
    private String buildDescription(Method method, McpTool mcpTool, String path, String httpMethod) {
        // 1. 优先使用注解中的描述
        if (!mcpTool.description().isEmpty()) {
            return mcpTool.description();
        }

        // 2. 从 Swagger 获取描述
        String swaggerDesc = swaggerParser.getOperationDescription(path, httpMethod);
        if (swaggerDesc != null && !swaggerDesc.isEmpty()) {
            log.debug("从 Swagger 获取描述：{} {} -> {}", httpMethod, path, swaggerDesc);
            return swaggerDesc;
        }

        // 3. 使用方法名
        return method.getName();
    }

    /**
     * 构建输入 Schema（优先从 Swagger 获取）
     */
    private Map<String, Object> buildInputSchema(Method method, String path, String httpMethod) {
        // 1. 优先从 Swagger 获取 Schema
        log.debug("尝试从 Swagger 获取 Schema: {} {}", httpMethod, path);
        Map<String, Object> swaggerSchema = swaggerParser.getParametersSchema(path, httpMethod);

        // 检查 Swagger Schema 是否有效（properties 不为空）
        boolean hasValidProperties = swaggerSchema != null
                && !swaggerSchema.isEmpty()
                && swaggerSchema.containsKey("properties")
                && swaggerSchema.get("properties") instanceof Map
                && !((Map<?, ?>) swaggerSchema.get("properties")).isEmpty();

        Map<String, Object> schema;
        if (hasValidProperties) {
            int propCount = ((Map<?, ?>) swaggerSchema.get("properties")).size();
            log.info("✅ 从 Swagger 获取 Schema: {} {} ({} 个属性)", httpMethod, path, propCount);
            schema = swaggerSchema;
        } else {
            // 2. 从方法参数解析（向后兼容）
            log.info("⚠️ Swagger Schema 无效，回退到方法参数解析：{} {}", httpMethod, path);
            schema = buildInputSchemaFromParameters(method);

            int propCount = schema.containsKey("properties")
                    ? ((Map<?, ?>) schema.get("properties")).size() : 0;
            log.info("✅ 方法参数解析完成：{} {} ({} 个属性)", httpMethod, path, propCount);
        }

        // 3. 检查是否需要添加 token 参数（如果接口需要认证）
        McpTool mcpTool = method.getAnnotation(McpTool.class);
        if (mcpTool != null && mcpTool.requiresAuth()) {
            schema = addTokenParameter(schema, mcpTool);
        }

        return schema;
    }

    /**
     * 添加 token 参数到 Schema
     * @param schema 原始 Schema
     * @param mcpTool MCP 工具注解
     * @return 包含 token 参数的 Schema
     */
    private Map<String, Object> addTokenParameter(Map<String, Object> schema, McpTool mcpTool) {
        if (schema == null) {
            schema = new HashMap<>();
            schema.put("type", "object");
            schema.put("properties", new HashMap<>());
        }

        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        if (properties == null) {
            properties = new HashMap<>();
            schema.put("properties", properties);
        }

        // 添加 token 参数
        Map<String, Object> tokenSchema = new HashMap<>();
        tokenSchema.put("type", "string");

        String description = "认证 Token（" + mcpTool.authType() + " 认证）";

        // 根据 tokenSource 决定是否必填
        boolean required = "param".equals(mcpTool.tokenSource());
        if (required) {
            description += "（必填）";
        } else {
            description += "（可选，从配置读取）";
        }
        tokenSchema.put("description", description);

        // ⚠️ 注意：不要在字段级别设置 required！
        // required 应该在对象级别的 required 数组中

        properties.put("token", tokenSchema);

        // 添加到 required 列表
        List<String> requiredList = (List<String>) schema.get("required");
        if (requiredList == null) {
            requiredList = new ArrayList<>();
            schema.put("required", requiredList);
        }
        if (required && !requiredList.contains("token")) {
            requiredList.add("token");
        }

        log.debug("添加 token 参数到 Schema: required={}", required);
        return schema;
    }

    /**
     * 从方法参数构建输入 Schema（向后兼容）
     * 支持 @RequestParam, @PathVariable, @RequestBody
     */
    private Map<String, Object> buildInputSchemaFromParameters(Method method) {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new HashMap<>();
        List<String> required = new ArrayList<>();

        // 解析方法参数
        Parameter[] parameters = method.getParameters();
        for (Parameter param : parameters) {
            String paramName = getParamName(param);

            // 1. @RequestParam - 查询参数
            if (param.isAnnotationPresent(RequestParam.class)) {
                RequestParam rp = param.getAnnotation(RequestParam.class);
                Map<String, Object> paramSchema = getParamSchema(param);
                properties.put(paramName, paramSchema);

                // 检查是否必填
                if (rp.required() && rp.defaultValue().isEmpty()) {
                    required.add(paramName);
                }
            }
            // 2. @PathVariable - 路径参数
            else if (param.isAnnotationPresent(PathVariable.class)) {
                PathVariable pv = param.getAnnotation(PathVariable.class);
                Map<String, Object> paramSchema = getParamSchema(param);
                properties.put(paramName, paramSchema);
                required.add(paramName);
            }
            // 3. @RequestBody - 请求体对象（展开对象属性）
            else if (param.isAnnotationPresent(RequestBody.class)) {
                log.debug("处理 @RequestBody 参数：{} {}", param.getType().getSimpleName(), paramName);

                // 展开对象的所有字段
                ExpandResult expandResult = expandRequestBodyProperties(param.getType());
                properties.putAll(expandResult.properties);
                required.addAll(expandResult.requiredFields);

                log.debug("@RequestBody 展开属性：{} 个，必填：{} 个",
                        expandResult.properties.size(), expandResult.requiredFields.size());
            }
        }

        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }

        log.debug("方法参数解析完成：properties={}, required={}", properties.size(), required);
        return schema;
    }

    /**
     * 展开 @RequestBody 对象的属性结果
     */
    @lombok.Data
    @lombok.Builder
    private static class ExpandResult {
        private Map<String, Object> properties;
        private List<String> requiredFields;
    }

    /**
     * 展开 @RequestBody 对象的属性
     * @param type 请求体类型（如 User.class）
     * @return 对象的所有属性 Schema 和必填字段列表
     */
    private ExpandResult expandRequestBodyProperties(Class<?> type) {
        Map<String, Object> properties = new HashMap<>();
        List<String> requiredFields = new ArrayList<>();

        // 递归处理所有字段（包括父类）
        Class<?> clazz = type;
        while (clazz != null && clazz != Object.class) {
            Field[] fields = clazz.getDeclaredFields();

            for (Field field : fields) {
                String fieldName = field.getName();

                // 跳过静态字段和 transient 字段
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) ||
                        java.lang.reflect.Modifier.isTransient(field.getModifiers())) {
                    continue;
                }

                // 构建字段 Schema
                Map<String, Object> fieldSchema = new HashMap<>();
                fieldSchema.put("type", getJavaTypeToJsonType(field.getType()));

                // 检查 Swagger @Schema 注解
                if (field.isAnnotationPresent(Schema.class)) {
                    Schema schema =
                            field.getAnnotation(Schema.class);

                    if (schema.description() != null && !schema.description().isEmpty()) {
                        fieldSchema.put("description", schema.description());
                    }
                    if (schema.example() != null && !schema.example().isEmpty()) {
                        fieldSchema.put("example", schema.example());
                    }
                    if (schema.allowableValues().length > 0) {
                        fieldSchema.put("enum", Arrays.asList(schema.allowableValues()));
                    }
                    // 检查是否必填（从 @Schema 注解）
                    if (schema.required()) {
                        requiredFields.add(fieldName);
                        log.debug("字段 {} 是必填（@Schema.required）", fieldName);
                    }
                }

                properties.put(fieldName, fieldSchema);
                log.debug("展开字段：{} - {}", fieldName, fieldSchema);
            }

            clazz = clazz.getSuperclass();
        }

        return ExpandResult.builder()
                .properties(properties)
                .requiredFields(requiredFields)
                .build();
    }

    /**
     * Java 类型转换为 JSON Schema 类型
     */
    private String getJavaTypeToJsonType(Class<?> type) {
        if (type == int.class || type == Integer.class ||
                type == long.class || type == Long.class) {
            return "integer";
        } else if (type == double.class || type == Double.class ||
                type == float.class || type == Float.class) {
            return "number";
        } else if (type == boolean.class || type == Boolean.class) {
            return "boolean";
        } else if (type == String.class) {
            return "string";
        } else if (type == Date.class || type == java.time.LocalDateTime.class) {
            return "string";
        } else if (type.isArray() || Collection.class.isAssignableFrom(type)) {
            return "array";
        } else {
            return "object";
        }
    }

    /**
     * 获取参数名称
     */
    private String getParamName(Parameter param) {
        // 从注解获取参数名
        if (param.isAnnotationPresent(RequestParam.class)) {
            RequestParam rp = param.getAnnotation(RequestParam.class);
            if (!rp.value().isEmpty()) {
                return rp.value();
            }
        }
        if (param.isAnnotationPresent(PathVariable.class)) {
            PathVariable pv = param.getAnnotation(PathVariable.class);
            if (!pv.value().isEmpty()) {
                return pv.value();
            }
        }
        if (param.isAnnotationPresent(RequestBody.class)) {
            return "body";
        }

        // 使用参数本来的名称
        return param.getName();
    }

    /**
     * 获取参数 Schema
     */
    private Map<String, Object> getParamSchema(Parameter param) {
        Map<String, Object> schema = new HashMap<>();
        Class<?> type = param.getType();

        // 基本类型映射
        if (type == int.class || type == Integer.class ||
                type == long.class || type == Long.class) {
            schema.put("type", "integer");
        } else if (type == double.class || type == Double.class ||
                type == float.class || type == Float.class) {
            schema.put("type", "number");
        } else if (type == boolean.class || type == Boolean.class) {
            schema.put("type", "boolean");
        } else if (type == String.class) {
            schema.put("type", "string");
        } else {
            // 复杂对象
            schema.put("type", "object");
            schema.put("description", type.getSimpleName());
        }

        // 添加描述
        if (param.isAnnotationPresent(RequestParam.class)) {
            RequestParam rp = param.getAnnotation(RequestParam.class);
            if (!rp.defaultValue().isEmpty() && !rp.defaultValue().equals("\n\t\t\n\t\t\n\uE000\uE001\uE002\n\t\t\t\t\n")) {
                schema.put("token", rp.defaultValue());
            }
        }

        return schema;
    }
}
