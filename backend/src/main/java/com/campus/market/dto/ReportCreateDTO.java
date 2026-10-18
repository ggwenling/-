package com.campus.market.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 举报提交请求体（PRD RPT-01）。
 * 字段级校验在此声明；白名单在 DTO 层用 @Pattern 先行拦截，业务存在性/自举报/重复举报在
 * ReportService 二次校验（PRD §6.5 举报入口）。
 */
@Getter
@Setter
public class ReportCreateDTO {

    /** 被举报对象类型：GOODS / WANT / SWAP / USER（白名单） */
    @NotBlank(message = "举报对象类型不能为空")
    @Pattern(regexp = "GOODS|WANT|SWAP|USER", message = "举报对象类型不合法")
    private String targetType;

    /** 被举报对象 ID */
    @NotNull(message = "举报对象不能为空")
    private Long targetId;

    /** 举报类型：VIOLATION / FRAUD / COUNTERFEIT / OTHER（白名单） */
    @NotBlank(message = "举报类型不能为空")
    @Pattern(regexp = "VIOLATION|FRAUD|COUNTERFEIT|OTHER", message = "举报类型不合法")
    private String reportType;

    /** 补充描述，≤500 字，选填 */
    @Size(max = 500, message = "补充描述不能超过 500 字")
    private String description;

    /** 举报截图 URL 列表，最多 3 张，选填（T10：JSON 列存储） */
    @Size(max = 3, message = "举报截图最多 3 张")
    private List<String> images;
}
