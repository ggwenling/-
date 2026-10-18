package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 校园认证实体，对应表 user_auth（PRD USR-03 / §5.1 / 数据库设计文档 §3.2）。
 * 一人一条记录；学号与校园邮箱全局唯一（uk 由数据库保证，业务层提前校验给出友好错误码）。
 * 验证码只存 Redis，不落库（T6）。
 */
@Getter
@Setter
@TableName("user_auth")
public class UserAuth {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_VERIFIED = 1;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID，1:1 */
    private Long userId;

    private String studentNo;

    private String campusEmail;

    private Integer status;

    private LocalDateTime verifiedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
