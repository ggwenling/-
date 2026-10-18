package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 敏感词实体，对应表 sensitive_word（PRD §5.8 内容安全 / 数据库设计文档 §3.22）。
 * DFA 全量内存加载（SensitiveWordService），后台增删后刷新缓存（刷新入口由 M6 调用）。
 */
@Getter
@Setter
@TableName("sensitive_word")
public class SensitiveWord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 敏感词，唯一 */
    private String word;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
