package com.getop.mcptool.common.constant;

/**
 * MCP 协议常量
 * <p>
 * 定义 MCP 协议相关的常量，避免魔法值。
 * </p>
 *
 * @author AI Assistant
 * @version 1.0.0
 * @since 2026-03-21
 */
public final class McpConstants {

    /**
     * 私有构造函数，防止实例化
     */
    private McpConstants() {
        throw new UnsupportedOperationException("Constants class cannot be instantiated");
    }

    // ================================================================
    // MCP 协议版本
    // ================================================================

    /**
     * 默认 MCP 协议版本
     */
    public static final String DEFAULT_PROTOCOL_VERSION = "2026-03-21";

    /**
     * JSON-RPC 版本
     */
    public static final String JSON_RPC_VERSION = "2.0";

    // ================================================================
    // MCP 方法名
    // ================================================================

    /**
     * 初始化方法
     */
    public static final String METHOD_INITIALIZE = "initialize";

    /**
     * 已初始化通知
     */
    public static final String METHOD_INITIALIZED = "notifications/initialized";

    /**
     * 工具列表查询
     */
    public static final String METHOD_TOOLS_LIST = "tools/list";

    /**
     * 工具调用
     */
    public static final String METHOD_TOOLS_CALL = "tools/call";

    // ================================================================
    // JSON-RPC 字段名
    // ================================================================

    /**
     * JSON-RPC 版本字段
     */
    public static final String FIELD_JSONRPC = "jsonrpc";

    /**
     * 请求 ID 字段
     */
    public static final String FIELD_ID = "id";

    /**
     * 方法名字段
     */
    public static final String FIELD_METHOD = "method";

    /**
     * 参数字段
     */
    public static final String FIELD_PARAMS = "params";

    /**
     * 结果字段
     */
    public static final String FIELD_RESULT = "result";

    /**
     * 错误字段
     */
    public static final String FIELD_ERROR = "error";

    // ================================================================
    // 工具相关字段
    // ================================================================

    /**
     * 工具名称字段
     */
    public static final String FIELD_TOOLS = "tools";

    /**
     * 工具名称
     */
    public static final String FIELD_TOOL_NAME = "name";

    /**
     * 工具描述
     */
    public static final String FIELD_TOOL_DESCRIPTION = "description";

    /**
     * 工具输入 Schema
     */
    public static final String FIELD_TOOL_INPUT_SCHEMA = "inputSchema";

    /**
     * 工具调用参数
     */
    public static final String FIELD_TOOL_ARGUMENTS = "arguments";

    // ================================================================
    // 响应内容字段
    // ================================================================

    /**
     * 内容字段
     */
    public static final String FIELD_CONTENT = "content";

    /**
     * 内容类型
     */
    public static final String FIELD_CONTENT_TYPE = "type";


    /**
     * 内容类型：文本
     */
    public static final String CONTENT_TYPE_TEXT = "text";

    /**
     * 数据字段
     */
    public static final String FIELD_DATA = "data";

    // ================================================================
    // 错误相关字段
    // ================================================================

    /**
     * 错误消息字段
     */
    public static final String FIELD_ERROR_MESSAGE = "message";

    /**
     * 错误码字段
     */
    public static final String FIELD_ERROR_CODE = "code";

    /**
     * 错误类型字段
     */
    public static final String FIELD_ERROR_TYPE = "type";

    // ================================================================
    // 服务器信息字段
    // ================================================================

    /**
     * 协议版本字段
     */
    public static final String FIELD_PROTOCOL_VERSION = "protocolVersion";

    /**
     * 能力字段
     */
    public static final String FIELD_CAPABILITIES = "capabilities";

    /**
     * 服务器信息字段
     */
    public static final String FIELD_SERVER_INFO = "serverInfo";

    /**
     * 服务器名称
     */
    public static final String FIELD_SERVER_NAME = "name";

    /**
     * 服务器版本
     */
    public static final String FIELD_SERVER_VERSION = "version";

    /**
     * 客户端信息字段
     */
    public static final String FIELD_CLIENT_INFO = "clientInfo";

    // ================================================================
    // SSE 相关
    // ================================================================

    /**
     * SSE 端点事件名
     */
    public static final String SSE_EVENT_ENDPOINT = "endpoint";

    /**
     * SSE 心跳事件名
     */
    public static final String SSE_EVENT_PING = "ping";

    /**
     * SSE 会话 ID 参数名
     */
    public static final String SSE_PARAM_SESSION_ID = "sessionId";

    // ================================================================
    // 默认值
    // ================================================================

    /**
     * 默认服务器名称
     */
    public static final String DEFAULT_SERVER_NAME = "mcp-tool-service";

    /**
     * 默认服务器版本
     */
    public static final String DEFAULT_SERVER_VERSION = "1.0.0";

    /**
     * 默认 MCP 端点路径
     */
    public static final String DEFAULT_MCP_PATH = "/mcp";

    /**
     * 默认 SSE 超时时间（毫秒）
     */
    public static final long DEFAULT_SSE_TIMEOUT_MS = 0L; // 0 表示永不过期

    // ================================================================
    // 配置键名
    // ================================================================

    /**
     * MCP 服务器名称配置键
     */
    public static final String CONFIG_SERVER_NAME = "mcp.server.name";

    /**
     * MCP 服务器版本配置键
     */
    public static final String CONFIG_SERVER_VERSION = "mcp.server.version";

    /**
     * MCP 端点路径配置键
     */
    public static final String CONFIG_SERVER_PATH = "mcp.server.path";

    /**
     * MCP 协议版本配置键
     */
    public static final String CONFIG_PROTOCOL_VERSION = "mcp.protocol.version";

    // ================================================================
    // 日志消息模板
    // ================================================================

    /**
     * 工具调用成功日志模板
     */
    public static final String LOG_TOOL_CALL_SUCCESS = "✅ 工具调用成功：{}";

    /**
     * 工具调用失败日志模板
     */
    public static final String LOG_TOOL_CALL_FAILED = "❌ 工具调用失败：{}";

    /**
     * SSE 连接成功日志模板
     */
    public static final String LOG_SSE_CONNECTED = "✅ SSE 连接建立成功：{}";

    /**
     * SSE 连接关闭日志模板
     */
    public static final String LOG_SSE_DISCONNECTED = "🔴 SSE 连接关闭：{}";

    /**
     * SSE 连接超时日志模板
     */
    public static final String LOG_SSE_TIMEOUT = "⏰ SSE 连接超时：{}";

    /**
     * 未知方法日志模板
     */
    public static final String LOG_UNKNOWN_METHOD = "⚠️ 未知的 MCP 方法：{}";
}
