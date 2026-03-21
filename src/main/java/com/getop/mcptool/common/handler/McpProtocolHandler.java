package com.getop.mcptool.common.handler;

import com.getop.mcptool.common.McpToolInvoker;
import com.getop.mcptool.common.McpToolRegistry;
import com.getop.mcptool.common.constant.McpConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 协议处理器
 * <p>
 * 通用的 MCP 协议实现，负责 MCP 协议和 HTTP 协议之间的转换。
 * 可以应用到任何 Spring Boot 服务，无需修改代码。
 * </p>
 * 
 * <h3>功能特性</h3>
 * <ul>
 *     <li>支持 MCP 标准协议（JSON-RPC 2.0）</li>
 *     <li>支持 SSE 长连接</li>
 *     <li>支持工具列表查询</li>
 *     <li>支持工具调用</li>
 *     <li>完全通用，不依赖具体业务</li>
 * </ul>
 * 
 * <h3>使用方式</h3>
 * <pre>
 * {@code
 * // 1. 添加依赖（已包含在 mcp-tool-common 中）
 * 
 * // 2. 配置 application.yml
 * mcp:
 *   server:
 *     name: my-service
 *     version: 1.0.0
 *     path: /mcp  // MCP 端点路径
 * 
 * // 3. 启动服务后自动生效
 * }
 * </pre>
 * 
 * @author AI Assistant
 * @version 1.0.0
 * @since 2026-03-21
 */
@Slf4j
@RestController
@RequestMapping("${" + McpConstants.CONFIG_SERVER_PATH + ":" + McpConstants.DEFAULT_MCP_PATH + "}")
public class McpProtocolHandler {
    
    /**
     * SSE 连接会话存储
     */
    private final Map<String, SseEmitter> sessions = new ConcurrentHashMap<>();
    
    /**
     * 工具注册表
     */
    private final McpToolRegistry toolRegistry;
    
    /**
     * 工具调用器
     */
    private final McpToolInvoker toolInvoker;
    
    /**
     * MCP 服务器配置
     */
    @Value("${" + McpConstants.CONFIG_SERVER_NAME + ":" + McpConstants.DEFAULT_SERVER_NAME + "}")
    private String serverName;
    
    @Value("${" + McpConstants.CONFIG_SERVER_VERSION + ":" + McpConstants.DEFAULT_SERVER_VERSION + "}")
    private String serverVersion;
    
    @Value("${" + McpConstants.CONFIG_PROTOCOL_VERSION + ":" + McpConstants.DEFAULT_PROTOCOL_VERSION + "}")
    private String protocolVersion;
    
    /**
     * 构造函数
     * 
     * @param toolRegistry 工具注册表
     * @param toolInvoker  工具调用器
     */
    public McpProtocolHandler(McpToolRegistry toolRegistry, 
                             McpToolInvoker toolInvoker) {
        this.toolRegistry = toolRegistry;
        this.toolInvoker = toolInvoker;
        
        log.info("✅ MCP 协议处理器初始化完成");
        log.info("  服务名称：{}", serverName);
        log.info("  服务版本：{}", serverVersion);
        log.info("  协议版本：{}", protocolVersion);
        log.info("  MCP 端点：{}", McpConstants.DEFAULT_MCP_PATH);
    }
    
    // ================================================================
    // SSE 连接管理
    // ================================================================
    
    /**
     * SSE 连接端点（GET）
     * <p>
     * MCP Client 会先连接这个端点建立 SSE 长连接。
     * 服务器通过 SSE 推送通知和心跳。
     * </p>
     * 
     * @param sessionId 会话 ID（可选，为空时自动生成）
     * @return SSE 发射器
     */
    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connect(@RequestParam(required = false) String sessionId) {
        log.debug("新的 SSE 连接请求，sessionId: {}", sessionId);
        
        String id = sessionId != null ? sessionId : UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(0L); // 永不过期
        sessions.put(id, emitter);
        
        try {
            // 发送 SSE 端点信息（MCP 协议要求）
            SseEmitter.SseEventBuilder event = SseEmitter.event()
                .name(McpConstants.SSE_EVENT_ENDPOINT)
                .data(getMcpPath() + "?" + McpConstants.SSE_PARAM_SESSION_ID + "=" + id);
            emitter.send(event);
            
            // 发送心跳
            emitter.send(SseEmitter.event().name(McpConstants.SSE_EVENT_PING).data(""));
            
            log.info(McpConstants.LOG_SSE_CONNECTED, id);
        } catch (IOException e) {
            log.error("❌ SSE 连接建立失败：{}", id, e);
            emitter.completeWithError(e);
        }
        
        // 连接关闭时的清理
        emitter.onCompletion(() -> {
            sessions.remove(id);
            log.info(McpConstants.LOG_SSE_DISCONNECTED, id);
        });
        
        emitter.onTimeout(() -> {
            sessions.remove(id);
            log.info(McpConstants.LOG_SSE_TIMEOUT, id);
        });
        
        return emitter;
    }
    
    // ================================================================
    // MCP 消息处理
    // ================================================================
    
    /**
     * 消息处理端点（POST）
     * <p>
     * MCP Client 发送 JSON-RPC 2.0 请求到这里。
     * 支持的请求类型：
     * </p>
     * <ul>
     *     <li>initialize - 初始化连接</li>
     *     <li>notifications/initialized - 客户端已初始化</li>
     *     <li>tools/list - 查询工具列表</li>
     *     <li>tools/call - 调用工具</li>
     * </ul>
     * 
     * @param request   MCP 请求（JSON-RPC 2.0 格式）
     * @param sessionId 会话 ID（可选）
     * @return MCP 响应
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> handleMessage(
            @RequestBody Map<String, Object> request,
            @RequestParam(required = false) String sessionId) {
        
        log.debug("收到 MCP 请求：{}", request.get("method"));
        
        String method = (String) request.get("method");
        Object id = request.get("id");
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        
        try {
            switch (method) {
                case McpConstants.METHOD_INITIALIZE:
                    return handleInitialize(id);
                
                case McpConstants.METHOD_INITIALIZED:
                    return handleInitialized();
                
                case McpConstants.METHOD_TOOLS_LIST:
                    return handleToolsList(id);
                
                case McpConstants.METHOD_TOOLS_CALL:
                    return handleToolsCall(request, id);
                
                default:
                    log.warn(McpConstants.LOG_UNKNOWN_METHOD, method);
                    return ResponseEntity.badRequest()
                        .body(mcpError(id, "Unknown method: " + method));
            }
        } catch (Exception e) {
            log.error("❌ MCP 请求处理失败：{}", method, e);
            return ResponseEntity.internalServerError()
                .body(mcpError(id, "Internal error: " + e.getMessage()));
        }
    }
    
    // ================================================================
    // MCP 方法处理
    // ================================================================
    
    /**
     * 处理 initialize 请求
     */
    private ResponseEntity<Map<String, Object>> handleInitialize(Object id) {
        log.info("🔌 MCP 客户端初始化请求");
        return ResponseEntity.ok(mcpResponse(id, Map.of(
            McpConstants.FIELD_PROTOCOL_VERSION, protocolVersion,
            McpConstants.FIELD_CAPABILITIES, Map.of(McpConstants.FIELD_TOOLS, Map.of()),
            McpConstants.FIELD_SERVER_INFO, Map.of(
                McpConstants.FIELD_SERVER_NAME, serverName,
                McpConstants.FIELD_SERVER_VERSION, serverVersion
            )
        )));
    }
    
    /**
     * 处理 initialized 通知
     */
    private ResponseEntity<Map<String, Object>> handleInitialized() {
        log.debug("👍 MCP 客户端已初始化");
        return ResponseEntity.ok().build();
    }
    
    /**
     * 处理 tools/list 请求
     */
    private ResponseEntity<Map<String, Object>> handleToolsList(Object id) {
        List<Map<String, Object>> toolList = toolRegistry.getToolList();
        log.info("📋 返回工具列表，共 {} 个工具", toolList.size());
        
        if (log.isDebugEnabled()) {
            log.debug("工具列表详情:");
            for (Map<String, Object> tool : toolList) {
                log.debug("  - 工具名称：{}, 描述：{}", 
                    tool.get(McpConstants.FIELD_TOOL_NAME), 
                    tool.get(McpConstants.FIELD_TOOL_DESCRIPTION));
            }
        }
        
        return ResponseEntity.ok(mcpResponse(id, Map.of(
            McpConstants.FIELD_TOOLS, toolList
        )));
    }
    
    /**
     * 处理 tools/call 请求
     */
    private ResponseEntity<Map<String, Object>> handleToolsCall(
            Map<String, Object> request, Object id) {
        
        Map<String, Object> params = (Map<String, Object>) request.get(McpConstants.FIELD_PARAMS);
        String toolName = (String) params.get(McpConstants.FIELD_TOOL_NAME);
        Map<String, Object> arguments = (Map<String, Object>) params.get(McpConstants.FIELD_TOOL_ARGUMENTS);
        
        log.info("🔧 调用工具：{}, 参数：{}", toolName, arguments);
        
        try {
            // 使用工具调用器调用接口
            Object result = toolInvoker.invoke(toolName, arguments);
            
            log.info(McpConstants.LOG_TOOL_CALL_SUCCESS, toolName);
            
            // 将结果转换为易读的文本格式（大模型能看到）
            String resultText = formatResultForLLM(result);
            
            return ResponseEntity.ok(mcpResponse(id, Map.of(
                McpConstants.FIELD_CONTENT, List.of(
                    Map.of(
                        McpConstants.FIELD_CONTENT_TYPE, McpConstants.CONTENT_TYPE_TEXT,
                        McpConstants.CONTENT_TYPE_TEXT, resultText
                    )
                ),
                McpConstants.FIELD_DATA, result
            )));
            
        } catch (Exception e) {
            log.error(McpConstants.LOG_TOOL_CALL_FAILED, toolName, e);
            return ResponseEntity.internalServerError()
                .body(mcpError(id, "Tool call error: " + e.getMessage()));
        }
    }
    
    // ================================================================
    // 结果格式化
    // ================================================================
    
    /**
     * 将工具调用结果格式化为大模型易读的文本
     * 
     * @param result 原始结果
     * @return 格式化的文本
     */
    private String formatResultForLLM(Object result) {
        if (result == null) {
            return "调用成功，但未返回数据";
        }
        
        if (result instanceof Map) {
            Map<String, Object> resultMap = (Map<String, Object>) result;
            
            // 检查是否有 success 字段
            Boolean success = (Boolean) resultMap.get("success");
            if (success != null && !success) {
                return "调用失败：" + resultMap.get("message");
            }
            
            StringBuilder sb = new StringBuilder();
            
            // 添加 message
            String message = (String) resultMap.get("message");
            if (message != null) {
                sb.append(message).append("\n\n");
            }
            
            // 添加 data
            Object data = resultMap.get("data");
            if (data != null) {
                sb.append(formatDataForLLM(data));
            }
            
            return sb.length() > 0 ? sb.toString() : resultMap.toString();
        }
        
        return result.toString();
    }
    
    /**
     * 格式化数据为大模型易读的文本
     * 
     * @param data 数据
     * @return 格式化的文本
     */
    private String formatDataForLLM(Object data) {
        if (data == null) {
            return "无数据";
        }
        
        if (data instanceof List) {
            List<?> list = (List<?>) data;
            if (list.isEmpty()) {
                return "空列表";
            }
            
            StringBuilder sb = new StringBuilder();
            sb.append("共 ").append(list.size()).append(" 条记录:\n\n");
            
            // 只显示前 10 条
            int displayCount = Math.min(list.size(), 10);
            for (int i = 0; i < displayCount; i++) {
                sb.append("[").append(i + 1).append("] ");
                sb.append(formatMap((Map<?, ?>) list.get(i))).append("\n");
            }
            
            if (list.size() > 10) {
                sb.append("\n... 还有 ").append(list.size() - 10).append(" 条记录");
            }
            
            return sb.toString();
        }
        
        if (data instanceof Map) {
            return formatMap((Map<?, ?>) data);
        }
        
        return data.toString();
    }
    
    /**
     * 格式化 Map 为易读文本（隐藏敏感信息）
     * 
     * @param map Map 数据
     * @return 格式化的文本
     */
    private String formatMap(Map<?, ?> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) sb.append(", ");
            first = false;
            
            String key = entry.getKey() != null ? entry.getKey().toString() : "null";
            String value = entry.getValue() != null ? entry.getValue().toString() : "null";
            
            // 隐藏敏感信息
            String keyLower = key.toLowerCase();
            if (keyLower.contains("password") || keyLower.contains("token") || 
                keyLower.contains("secret") || keyLower.contains("key")) {
                value = "***";
            }
            
            sb.append(key).append(": ").append(value);
        }
        
        sb.append("}");
        return sb.toString();
    }
    
    // ================================================================
    // 辅助方法
    // ================================================================
    
    /**
     * 获取 MCP 端点路径
     * 
     * @return MCP 端点路径
     */
    private String getMcpPath() {
        return "/mcp";
    }
    
    /**
     * 构建 MCP 成功响应
     * 
     * @param id     请求 ID
     * @param result 响应结果
     * @return MCP 响应
     */
    private Map<String, Object> mcpResponse(Object id, Object result) {
        Map<String, Object> response = new HashMap<>();
        response.put(McpConstants.FIELD_JSONRPC, McpConstants.JSON_RPC_VERSION);
        response.put(McpConstants.FIELD_ID, id);
        response.put(McpConstants.FIELD_RESULT, result);
        return response;
    }
    
    /**
     * 构建 MCP 错误响应
     * 
     * @param id      请求 ID
     * @param message 错误消息
     * @return MCP 错误响应
     */
    private Map<String, Object> mcpError(Object id, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put(McpConstants.FIELD_JSONRPC, McpConstants.JSON_RPC_VERSION);
        response.put(McpConstants.FIELD_ID, id);
        Map<String, Object> error = new HashMap<>();
        error.put(McpConstants.FIELD_ERROR_MESSAGE, message);
        response.put(McpConstants.FIELD_ERROR, error);
        return response;
    }
}
