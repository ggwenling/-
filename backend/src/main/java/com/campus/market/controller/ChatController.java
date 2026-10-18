package com.campus.market.controller;

import com.campus.market.common.api.ErrorCode;
import com.campus.market.common.api.PageResult;
import com.campus.market.common.api.Result;
import com.campus.market.common.exception.BusinessException;
import com.campus.market.dto.MessageSendDTO;
import com.campus.market.security.LoginUser;
import com.campus.market.security.UserContext;
import com.campus.market.service.ChatService;
import com.campus.market.vo.ChatUnreadVO;
import com.campus.market.vo.ConversationVO;
import com.campus.market.vo.MessageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 私信接口（PRD CHT-01~06）：全部需登录且仅限前台用户主体。
 * 会话与消息仅会话双方可见（service 层二次校验，PRD §9.2）；
 * 消息先落库再经 WebSocket /ws 在线推送（CHT-02），离线由拉取兜底（CHT-05）。
 */
@Tag(name = "聊天-私信")
@Validated
@RestController
@RequestMapping("/api/chats")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @Operation(summary = "会话分页列表（CHT-01）")
    @GetMapping("/conversations")
    public Result<PageResult<ConversationVO>> conversations(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码最小为 1") long pageNum,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "每页条数最小为 1")
            @Max(value = 100, message = "每页条数最大为 100") long pageSize) {
        return Result.ok(chatService.pageConversations(requireFrontUser().getUserId(), pageNum, pageSize));
    }

    @Operation(summary = "发送私信（CHT-02/03/06，TEXT 过敏感词，GOODS_CARD 传商品ID）")
    @PostMapping("/messages")
    public Result<MessageVO> send(@Valid @RequestBody MessageSendDTO dto) {
        return Result.ok(chatService.sendMessage(requireFrontUser().getUserId(), dto));
    }

    @Operation(summary = "会话消息分页（倒序；拉取即已读，CHT-02/05）")
    @GetMapping("/conversations/{conversationId}/messages")
    public Result<PageResult<MessageVO>> messages(
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码最小为 1") long pageNum,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "每页条数最小为 1")
            @Max(value = 100, message = "每页条数最大为 100") long pageSize) {
        return Result.ok(chatService.pageMessages(requireFrontUser().getUserId(), conversationId, pageNum, pageSize));
    }

    @Operation(summary = "标记会话已读（CHT-05，幂等）")
    @PostMapping("/conversations/{conversationId}/read")
    public Result<Void> markRead(@PathVariable Long conversationId) {
        chatService.markRead(requireFrontUser().getUserId(), conversationId);
        return Result.ok();
    }

    @Operation(summary = "未读聚合（会话数+消息总数，导航红点，CHT-05）")
    @GetMapping("/unread")
    public Result<ChatUnreadVO> unread() {
        return Result.ok(chatService.countUnread(requireFrontUser().getUserId()));
    }

    /** 聊天仅限前台用户主体（管理员无私信语义） */
    private LoginUser requireFrontUser() {
        LoginUser user = UserContext.requireLogin();
        if (user.getUserType() != LoginUser.UserType.USER) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return user;
    }
}
