package com.campus.market.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 发送私信请求体（PRD CHT-02/03，POST /api/chats/messages）。
 * content 语义随 msgType 变化：TEXT=文本（≤500 字，过敏感词 DFA）；GOODS_CARD=商品 ID 字符串；
 * IMAGE 为 P2 预留（CHT-04 本期不开放）。
 */
@Getter
@Setter
public class MessageSendDTO {

    /** 会话对方用户 ID（服务端约定 user1_id&lt;user2_id 归一化落库） */
    @NotNull(message = "对方用户不能为空")
    private Long peerUserId;

    /** 消息类型：TEXT / GOODS_CARD（IMAGE 预留） */
    @NotBlank(message = "消息类型不能为空")
    private String msgType;

    /** TEXT=文本 / GOODS_CARD=商品 ID 字符串 */
    @NotBlank(message = "消息内容不能为空")
    @Size(max = 500, message = "消息内容不能超过 500 字")
    private String content;
}
