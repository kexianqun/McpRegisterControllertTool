package com.getop.mcptool.common.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token 管理器
 * 管理各种认证 Token
 */
@Slf4j
@Component
public class TokenManager {
    
    /**
     * 从配置文件中读取的 Token
     */
    @Value("${mcp.auth.default.token:}")
    private String defaultToken;
    
    /**
     * 动态 Token 存储（运行时设置）
     */
    private final Map<String, String> tokenStore = new ConcurrentHashMap<>();
    
    /**
     * 获取 Token
     * @param key Token 键名
     * @return Token 值
     */
    public String getToken(String key) {
        // 1. 先尝试动态 Token
        String token = tokenStore.get(key);
        if (token != null) {
            log.debug("获取动态 Token: {} = {}", key, maskToken(token));
            return token;
        }
        
        // 2. 使用默认 Token
        if ("token".equals(key) && defaultToken != null && !defaultToken.isEmpty()) {
            log.debug("使用默认 Token: {}", maskToken(defaultToken));
            return defaultToken;
        }
        
        log.warn("未找到 Token: {}", key);
        return null;
    }
    
    /**
     * 设置动态 Token
     * @param key Token 键名
     * @param token Token 值
     */
    public void setToken(String key, String token) {
        tokenStore.put(key, token);
        log.info("设置动态 Token: {} = {}", key, maskToken(token));
    }
    
    /**
     * 移除 Token
     * @param key Token 键名
     */
    public void removeToken(String key) {
        tokenStore.remove(key);
        log.info("移除 Token: {}", key);
    }
    
    /**
     * 掩码 Token（日志输出用）
     */
    private String maskToken(String token) {
        if (token == null || token.length() < 8) {
            return "***";
        }
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }
}
