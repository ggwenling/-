package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品实体，对应表 goods（PRD GDS-01~08 / 数据库设计文档 §3.6）。
 * 软删除走状态值 DELETED + deleted_* 审计四元组（T4），不使用 MyBatis-Plus @TableLogic。
 * 派生字段 view_count / want_count / favorite_count / heat_score 的事实来源见数据库设计文档 §3.0（T5）。
 */
@Getter
@Setter
@TableName("goods")
public class Goods {

    /** 商品状态机（PRD §5.1） */
    public static final String STATUS_ON_SALE = "ON_SALE";
    public static final String STATUS_IN_TRANSACTION = "IN_TRANSACTION";
    public static final String STATUS_SOLD = "SOLD";
    public static final String STATUS_OFF_SALE = "OFF_SALE";
    public static final String STATUS_DELETED = "DELETED";

    /** 删除操作者类型（T4） */
    public static final String DELETED_BY_USER = "USER";
    public static final String DELETED_BY_ADMIN = "ADMIN";

    /** 卖家软删商品的默认原因（T4 删除规则表） */
    public static final String USER_DELETE_REASON = "卖家自行删除";

    /** 卖家自行删除后允许恢复的最大天数（T4：30 天内可自行恢复） */
    public static final int USER_RESTORE_DAYS = 30;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 卖家 ID（FK user.id） */
    private Long userId;

    /** 二级分类 ID（FK category.id） */
    private Long categoryId;

    /** 标题，≤50 字 */
    private String title;

    /** 描述，≤500 字 */
    private String description;

    /** 面交价，0=免费赠送，不得为负 */
    private BigDecimal price;

    /** 成色：1全新 / 2几乎全新 / 3轻微使用痕迹 / 4明显使用痕迹 */
    private Integer conditionLevel;

    /** 教材课程名（分类=教材书籍时选填，PRD GDS-07） */
    private String courseName;

    /** ISBN（同上） */
    private String isbn;

    /** 常约交易地点 */
    private String tradeLocation;

    /** 状态：ON_SALE / IN_TRANSACTION / SOLD / OFF_SALE / DELETED */
    private String status;

    /** 下架理由（管理员下架必填，用户自行下架为空） */
    private String offSaleReason;

    /** 软删时间（T4） */
    private LocalDateTime deletedAt;

    /** 删除操作者类型：USER / ADMIN（T4） */
    private String deletedByType;

    /** 删除操作者 ID（对应 user.id 或 admin.id，逻辑外键） */
    private Long deletedBy;

    /** 删除原因 */
    private String deleteReason;

    /** 浏览量（派生，事实=user_behavior，浏览埋点同事务 +1，T5/T9） */
    private Long viewCount;

    /** 想要数（派生，事实=goods_want；M2 只展示不写入，M3 下单时维护） */
    private Long wantCount;

    /** 收藏数（派生，事实=favorite，收藏/取消同事务 ±1，T5） */
    private Long favoriteCount;

    /** 热度分 = 浏览×1 + 收藏×3 + 想要×5（定时任务重算，本模块只读） */
    private Integer heatScore;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
