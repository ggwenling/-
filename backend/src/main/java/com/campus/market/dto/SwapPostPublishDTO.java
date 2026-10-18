package com.campus.market.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 交换帖发布/编辑请求体（PRD SWP-01 发布/编辑）。
 * 差价联动约束（allow_diff=1 → diff_amount 必填且 ≥0；allow_diff=0 → diff_amount 置 NULL）在 SwapPostService 校验。
 */
@Getter
@Setter
public class SwapPostPublishDTO {

    /** 标题，≤50 字 */
    @NotBlank(message = "标题不能为空")
    @Size(max = 50, message = "标题不能超过 50 字")
    private String title;

    /** 物品分类 ID（二级分类） */
    @NotNull(message = "请选择物品分类")
    private Long categoryId;

    /** 我的物品描述，≤500 字 */
    @NotBlank(message = "请描述自己的物品")
    @Size(max = 500, message = "物品描述不能超过 500 字")
    private String myItemDesc;

    /** 想要的物品描述，≤500 字 */
    @NotBlank(message = "请描述想要的物品")
    @Size(max = 500, message = "想要的物品描述不能超过 500 字")
    private String wantItemDesc;

    /** 是否接受补差价：0 否 / 1 是（前端 el-switch 联动差额输入） */
    @NotNull(message = "请选择是否接受补差价")
    private Integer allowDiff;

    /** 期望差价金额，allowDiff=1 时必填且 ≥0 */
    private BigDecimal diffAmount;
}
