package com.getop.mcptool.controller;

import com.getop.mcptool.common.annotation.McpTool;
import com.getop.mcptool.domain.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户管理 Controller
 * 提供用户查询、更新接口
 */
@Tag(name = "用户管理", description = "用户信息管理接口")
@RestController
@RequestMapping("/api/users")
public class UserController {
    /**
     * 查询用户列表
     * @param page 页码
     * @param pageSize 每页数量
     * @return 用户列表
     */
    @GetMapping("/list")
    @McpTool(description = "查询用户列表，支持分页")
    @Operation(summary = "查询用户列表", description = "分页查询所有用户信息")
    public Map<String, Object> queryUserList(
            @Parameter(description = "页码，从 1 开始", example = "1")
            @RequestParam(value = "page", defaultValue = "1") int page,
            
            @Parameter(description = "每页数量", example = "10")
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        List<User> users = User.getUserList();
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", users);
        result.put("total", users.size());
        result.put("page", page);
        result.put("pageSize", pageSize);
        
        return result;
    }
    
    /**
     * 根据 ID 查询用户详情
     * @param userId 用户 ID
     * @return 用户详情
     */
    @GetMapping("/detail/{userId}")
    @McpTool(description = "根据用户 ID 查询详细信息", requiresAuth = true)
    @Operation(summary = "查询用户详情", description = "根据用户 ID 获取详细信息")
    public Map<String, Object> getUserById(
            @Parameter(description = "用户 ID", required = true, example = "1")
            @PathVariable Long userId) {
        User user = new User(userId, "user" + userId, "PASSWORD", 25, "男", "user" + userId + "@example.com");
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", user.toString());
        return result;
    }
    
    /**
     * 更新用户信息
     * @param userId 用户 ID
     * @param username 用户名
     * @param email 邮箱
     * @param age 年龄
     * @return 更新结果
     */
    @PutMapping("/update/{userId}")
    @McpTool(description = "更新用户信息")
    @Operation(summary = "更新用户信息", description = "更新指定用户的详细信息")
    public Map<String, Object> updateUser(
            @Parameter(description = "用户 ID", required = true, example = "1")
            @PathVariable Long userId,
            
            @Parameter(description = "用户名", example = "zhangsan")
            @RequestParam(required = false) String username,
            
            @Parameter(description = "邮箱", example = "zhangsan@example.com")
            @RequestParam(required = false) String email,
            
            @Parameter(description = "年龄", example = "26")
            @RequestParam(required = false) Integer age) {
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "用户更新成功");
        result.put("userId", userId);
        result.put("updatedFields", Map.of(
            "username", username,
            "email", email,
            "age", age
        ));
        
        return result;
    }
    @PostMapping("/add")
    @McpTool(description = "增加用户信息")
    @Operation(summary = "增加用户信息", description = "增加用户信息")
    public Map<String, Object> addUser(@Parameter @RequestBody User user) {
        System.out.println("[UserController addUser] " + user);
        // 创建可变列表
        List<User> userList = new ArrayList<>(User.getUserList());
        userList.add(user);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "用户添加成功");
        result.put("data", userList);
        result.put("total", userList.size());
        return result;
    }
}
