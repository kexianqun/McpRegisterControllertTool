package com.getop.mcptool.common;

import com.getop.mcptool.common.model.McpToolDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 工具注册表
 * 管理所有可被 MCP 调用的工具
 * 支持通过规范化名称和原始路径查找工具
 */
@Slf4j
@Component
public class McpToolRegistry {
    
    /**
     * 工具注册表：工具名（规范化） -> 工具定义
     */
    private final Map<String, McpToolDefinition> tools = new ConcurrentHashMap<>();
    
    /**
     * 路径映射表：原始路径 -> 工具名（规范化）
     */
    private final Map<String, String> pathToNameMap = new ConcurrentHashMap<>();
    
    /**
     * 注册工具
     * @param tool 工具定义
     */
    public void register(McpToolDefinition tool) {
        tools.put(tool.getName(), tool);
        
        // 保存路径映射（用于内部查找）
        if (tool.getOriginalPath() != null && !tool.getOriginalPath().isEmpty()) {
            pathToNameMap.put(tool.getOriginalPath(), tool.getName());
        }
        
        log.info("注册 MCP 工具：{} (路径：{}) -> {}", 
            tool.getName(), tool.getOriginalPath(), tool.getUrl());
    }
    
    /**
     * 获取工具定义
     * @param toolName 工具名称（可以是规范化名称或原始路径）
     * @return 工具定义，不存在返回 null
     */
    public McpToolDefinition getTool(String toolName) {
        // 先尝试直接查找（规范化名称）
        McpToolDefinition tool = tools.get(toolName);
        
        // 如果没找到，尝试通过路径查找
        if (tool == null) {
            String normalizedName = pathToNameMap.get(toolName);
            if (normalizedName != null) {
                tool = tools.get(normalizedName);
            }
        }
        
        return tool;
    }
    
    /**
     * 获取所有工具
     * @return 所有工具的定义
     */
    public Map<String, McpToolDefinition> getAllTools() {
        return new ConcurrentHashMap<>(tools);
    }
    
    /**
     * 获取工具列表（用于 MCP tools/list 接口）
     * @return MCP 协议格式的工具列表
     */
    public List<Map<String, Object>> getToolList() {
        List<Map<String, Object>> toolList = new ArrayList<>();
        for (McpToolDefinition tool : tools.values()) {
            toolList.add(convertToMcpFormat(tool));
        }
        return toolList;
    }
    
    /**
     * 转换为 MCP 协议格式
     * @param tool 工具定义
     * @return MCP 格式的工具信息
     */
    private Map<String, Object> convertToMcpFormat(McpToolDefinition tool) {
        return Map.of(
            "name", tool.getName(),
            "description", tool.getDescription(),
            "inputSchema", tool.getInputSchema()
        );
    }
    
    /**
     * 注销工具
     * @param toolName 工具名称
     */
    public void unregister(String toolName) {
        tools.remove(toolName);
        log.info("注销 MCP 工具：{}", toolName);
    }
    
    /**
     * 清空所有工具
     */
    public void clear() {
        tools.clear();
        log.info("清空所有 MCP 工具");
    }
}
