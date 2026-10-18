package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 管理员实体，对应表 admin（PRD §4.2 RBAC / 数据库设计文档 §3.3）。
 * 角色口径：数据库存大写，代码与前端一律小写（docs/命名规范.md §3）。
 * 初始凭据经环境变量注入 + 幂等初始化 + 首次登录强制改密（T12），仓库不存明文。
 */
@Getter
@Setter
@TableName("admin")
public class Admin {

    public static final String ROLE_SUPER = "SUPER";
    public static final String ROLE_AUDITOR = "AUDITOR";
    public static final String ROLE_OPERATOR = "OPERATOR";

    public static final int STATUS_ENABLED = 0;
    public static final int STATUS_DISABLED = 1;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** BCrypt 密文 */
    private String password;

    private String realName;

    /** SUPER / AUDITOR / OPERATOR（DB 大写） */
    private String role;

    private Integer status;

    /** 首次登录强制改密标记（T12）：1=须改密，改密成功后置 0 */
    private Integer mustChangePassword;

    private LocalDateTime lastLoginAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
