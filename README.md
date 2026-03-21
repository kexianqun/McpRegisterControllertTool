# MCP Tool Common 使用指南

## 📋 功能概述

`mcp-tool-common` 是一个**通用的公共模块**，用于将 Spring Boot 服务的 Controller 接口自动注册为 MCP 工具，供 AgentScope 智能体调用。

**核心组件**：
- **McpProtocolHandler** - MCP 协议处理器（通用，可在任何服务中使用）
- **McpToolScanner** - 工具扫描器（自动扫描 Controller）
- **McpToolRegistry** - 工具注册表
- **McpToolInvoker** - 工具调用器
- **TokenManager** - 认证管理器
- **SwaggerParser** - Swagger 解析器

**适用场景**：
- ✅ 任何 Spring Boot 服务
- ✅ 无需修改业务代码
- ✅ 通过配置自定义

## 🚀 快速开始

### 1. 在 Controller 上使用 @McpTool 注解

```java
@RestController
@RequestMapping("/api/users")
public class UserController {
    
    @GetMapping("/list")
    @McpTool(description = "查询用户列表，支持分页")
    public Map<String, Object> queryUserList(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        // ...
    }
    
    @GetMapping("/{userId}")
    @McpTool(description = "根据用户 ID 查询详细信息")
    public Map<String, Object> getUserById(@PathVariable Long userId) {
        // ...
    }
}
```

### 2. 启动服务

服务启动后，`McpToolScanner` 会自动扫描所有带有 `@McpTool` 注解的方法，并注册为 MCP 工具。

### 3. 查看日志

```
扫描 Controller: com.getop.mcptool.controller.UserController. 基础路径：/api/users
注册 MCP 工具：/user/api/users/list -> http://127.0.0.1:8080/api/users/list
注册 MCP 工具：/user/api/users/{userId} -> http://127.0.0.1:8080/api/users/{userId}
Controller: UserController 注册了 2 个 MCP 工具
```

## 📝 @McpTool 注解说明

```java
@McpTool(
    description = "工具描述",      // 工具功能说明（可选，会从 Swagger 获取）
    requiresAuth = false          // 是否需要认证（暂未实现）
)
```

## ⚙️ 配置说明

### 完整配置示例

```yaml
server:
  port: 8080

mcp:
  # MCP 服务器配置
  server:
    name: user-mcp-service          # 服务名称（显示给客户端）
    version: 1.0.0                   # 服务版本
    path: /mcp                       # MCP 端点路径
  
  # MCP 协议版本
  protocol:
    version: 2024-11-05
  
  # 服务基础 URL（用于工具调用）
  service:
    base-url: http://127.0.0.1:${server.port}  # 支持变量引用
  
  # Swagger 集成配置
  swagger:
    enabled: true
```

### 配置项说明

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `mcp.server.name` | `mcp-tool-service` | MCP 服务器名称 |
| `mcp.server.version` | `1.0.0` | MCP 服务器版本 |
| `mcp.server.path` | `/mcp` | MCP 端点路径 |
| `mcp.protocol.version` | `2024-11-05` | MCP 协议版本 |
| `mcp.service.base-url` | `http://127.0.0.1:8080` | 服务基础 URL |
| `mcp.swagger.enabled` | `true` | 是否启用 Swagger 集成 |

### 多环境配置

#### 开发环境

```yaml
# application-dev.yml
mcp:
  service:
    base-url: http://127.0.0.1:8080
```

#### 生产环境

```yaml
# application-prod.yml
mcp:
  server:
    name: user-mcp-service-prod
  service:
    base-url: https://api.example.com
```

#### Docker 环境

```yaml
# application-docker.yml
mcp:
  service:
    base-url: http://${HOSTNAME:-localhost}:${SERVER_PORT:-8080}
```

## 🔗 Swagger 集成

### 启用 Swagger 集成

服务启动后会自动集成 Swagger，无需额外配置。

### 描述获取优先级

1. **@McpTool 注解的 description**（最高优先级）
2. **Swagger 的 summary/description**
3. **方法名**（默认）

### Schema 获取优先级

1. **Swagger 的 parameters**（最高优先级）
2. **方法参数反射解析**（向后兼容）

### 配置示例

```yaml
# application.yml
springdoc:
  api-docs:
    enabled: true
  swagger-ui:
    enabled: true
  packages-to-scan: com.getop.mcptool.controller

mcp:
  swagger:
    enabled: true  # 启用 Swagger 集成（默认 true）
```

### 复杂对象支持

Swagger 集成后，支持复杂对象的 Schema 解析：

```java
public class CreateUserRequest {
    private String username;
    private String email;
    private Integer age;
    private Address address;  // 嵌套对象
}

public class Address {
    private String city;
    private String street;
}
```

**生成的 Schema：**

```json
{
  "type": "object",
  "properties": {
    "username": {"type": "string"},
    "email": {"type": "string"},
    "age": {"type": "integer"},
    "address": {
      "type": "object",
      "properties": {
        "city": {"type": "string"},
        "street": {"type": "string"}
      }
    }
  },
  "required": ["username", "email"]
}
```

## 🔧 自动扫描规则

### 支持的 Controller 类型
- `@RestController`
- `@Controller`

### 支持的请求映射注解
- `@GetMapping`
- `@PostMapping`
- `@PutMapping`
- `@DeleteMapping`
- `@RequestMapping`

### 支持的参数类型
- `@RequestParam` - 查询参数
- `@PathVariable` - 路径参数
- `@RequestBody` - 请求体（POST/PUT）

### 参数类型映射

| Java 类型 | MCP Schema 类型 |
|----------|----------------|
| int, Integer | integer |
| long, Long | integer |
| double, Double | number |
| boolean, Boolean | boolean |
| String | string |
| 其他对象 | object |

## 🧪 测试 MCP 端点

### 1. 测试 SSE 连接

```bash
curl -N http://127.0.0.1:8080/mcp
```

### 2. 测试工具列表

```bash
curl -X POST http://127.0.0.1:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'
```

### 3. 测试工具调用

```bash
curl -X POST http://127.0.0.1:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/call",
    "params": {
      "name": "/user/api/users/list",
      "arguments": {"page": 1, "pageSize": 10}
    }
  }'
```

## 📁 模块结构

```
common/
├── McpAutoConfig.java          # 自动配置
├── McpToolInvoker.java         # 工具调用器
├── McpToolRegistry.java        # 工具注册表
├── McpToolScanner.java         # 工具扫描器
├── annotation/
│   └── McpTool.java            # MCP 工具注解
└── model/
    └── McpToolDefinition.java  # 工具定义模型
```

## 🎯 阶段规划

### ✅ 阶段一：基础功能
- 手动注册工具
- HTTP 调用转发
- 测试跑通

### ✅ 阶段二：自动扫描
- 扫描 Controller
- 解析请求映射注解
- 生成 inputSchema

### ✅ 阶段三：Swagger 集成
- 从 Swagger 获取接口描述
- 解析复杂参数 Schema
- 支持嵌套对象
- 支持 $ref 引用解析

### 🔜 阶段四：优化扩展
- 支持认证/鉴权
- 支持参数校验
- 支持异步调用
- 错误处理优化

## 💡 最佳实践

1. **每个公开接口都加上 @McpTool 注解**
2. **description 尽量详细**，便于大模型理解
3. **参数使用 @RequestParam 明确指定名称**
4. **复杂对象后期从 Swagger 获取 Schema**

## ❓ 常见问题

### Q: 工具名称是如何生成的？
A: 工具名称 = `/` + Controller 类名（去掉 Controller） + 接口路径
例如：`UserController` + `/api/users/list` = `/user/api/users/list`

### Q: 如何禁用某个接口的 MCP 注册？
A: 不要在该接口上添加 `@McpTool` 注解即可

### Q: 如何修改工具名称？
A: 后期会在 `@McpTool` 注解中添加 `name` 属性

---

**版本**: 1.0.0  
**最后更新**: 2026-03-21
