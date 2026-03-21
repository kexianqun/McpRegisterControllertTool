# MCP 工具认证配置指南

## 📋 认证方式

支持 4 种认证方式：

| 认证类型 | 说明 | 请求头格式 |
|---------|------|-----------|
| Bearer | JWT Token | `Authorization: Bearer <token>` |
| Basic | 基本认证 | `Authorization: Basic <base64>` |
| ApiKey | API Key | `X-API-Key: <key>` |
| Custom | 自定义 | 自定义请求头 |

## 🔧 使用方式

### 方式一：注解配置（推荐）

```java
@PostMapping("/add")
@McpTool(
    description = "增加用户信息",
    requiresAuth = true,        // 启用认证
    authType = "Bearer",        // 认证类型
    tokenSource = "config",     // Token 来源：config, param, header
    tokenConfigKey = "default"  // 从配置文件中读取的键名
)
public Map<String, Object> addUser(@RequestBody User user) {
    // ...
}
```

### 方式二：配置文件设置 Token

```yaml
# application.yml
mcp:
  auth:
    tokens:
      # 默认 Token
      default: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
      
      # 多个 Token
      user-service: user-token-xxx
      admin-service: admin-token-xxx
```

### 方式三：运行时动态设置 Token

```java
@Autowired
private TokenManager tokenManager;

// 设置 Token
tokenManager.setToken("default", "your-jwt-token");
tokenManager.setToken("user-service", "user-specific-token");

// 获取 Token
String token = tokenManager.getToken("default");
```

## 📝 完整示例

### Controller

```java
@RestController
@RequestMapping("/api/users")
public class UserController {
    
    // 需要 Bearer Token 认证
    @PostMapping("/add")
    @McpTool(
        description = "增加用户信息",
        requiresAuth = true,
        authType = "Bearer",
        tokenConfigKey = "default"
    )
    public Map<String, Object> addUser(@RequestBody User user) {
        // ...
    }
    
    // 需要 API Key 认证
    @GetMapping("/list")
    @McpTool(
        description = "查询用户列表",
        requiresAuth = true,
        authType = "ApiKey",
        tokenConfigKey = "user-service"
    )
    public List<User> queryUserList() {
        // ...
    }
    
    // 不需要认证
    @GetMapping("/{id}")
    @McpTool(description = "查询用户详情")
    public User getUserById(@PathVariable Long id) {
        // ...
    }
}
```

### 配置文件

```yaml
mcp:
  auth:
    tokens:
      default: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...  # JWT Token
      user-service: sk-user-xxx  # API Key
```

## 🧪 测试

### 1. 设置 Token

```bash
# 方式 1：配置文件
export USER_SERVICE_TOKEN=your-jwt-token

# 方式 2：运行时 API
curl -X POST http://127.0.0.1:8080/token/set \
  -H "Content-Type: application/json" \
  -d '{"key":"default","value":"your-jwt-token"}'
```

### 2. 调用需要认证的接口

```bash
curl -X POST http://127.0.0.1:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "name": "user_api_users_add",
      "arguments": {
        "username": "testuser",
        "password": "123456",
        "email": "test@example.com"
      }
    }
  }'
```

### 3. 查看日志

```
工具需要认证：user_api_users_add, 认证类型：Bearer
添加 Bearer Token: eyJh...wVCJ9
调用接口：POST http://127.0.0.1:8080/api/users/add
```

## ⚠️ 注意事项

1. **Token 安全**
   - 不要将 Token 硬编码在代码中
   - 使用环境变量或配置中心管理 Token
   - 日志中会自动掩码 Token

2. **Token 过期**
   - Token 过期时需要重新设置
   - 可以实现自动刷新 Token 的机制

3. **多 Token 管理**
   - 不同服务使用不同的 Token
   - 通过 `tokenConfigKey` 区分

---

**版本**: 1.0.0  
**最后更新**: 2026-03-21
