package com.campus.market.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 通用"动作 + 理由"请求体（M5 复用）：关闭帖子、拒绝应约/交换请求时携带可选理由。
 * 理由可选（帖子关闭/拒绝场景），但会写入通知内容，便于双方追溯。
 */
@Getter
@Setter
public class ActionReasonDTO {

    /** 理由，≤200 字 */
    @Size(max = 200, message = "理由不能超过 200 字")
    private String reason;
}
