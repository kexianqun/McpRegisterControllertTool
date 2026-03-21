package com.getop.mcptool.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户实体类
 * 使用 Swagger 注解描述字段信息
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "用户信息")
public class User implements Serializable {
    
    @Schema(description = "用户 ID", example = "1")
    private Long id;
    
    @Schema(description = "用户名", example = "zhangsan", required = true)
    private String username;
    
    @Schema(description = "密码", example = "123456", required = true)
    private String password;
    
    @Schema(description = "年龄", example = "25")
    private Integer age;
    
    @Schema(description = "性别", example = "男", allowableValues = {"男", "女"})
    private String gender;
    
    @Schema(description = "邮箱", example = "zhangsan@example.com", required = true)
    private String email;
    
    /**
     * 测试用：获取用户列表（返回可变列表）
     */
    public static java.util.List<User> getUserList() {
        java.util.List<User> userList = new java.util.ArrayList<>();
        userList.add(new User(1L, "zhangsan", "123456", 25, "男", "zhangsan@example.com"));
        userList.add(new User(2L, "lisi", "123456", 30, "女", "lisi@example.com"));
        userList.add(new User(3L, "wangwu", "123456", 28, "男", "wangwu@example.com"));
        return userList;
    }
}
