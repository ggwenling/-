package com.campus.market.service.impl;

import com.campus.market.common.MpTestSupport;
import com.campus.market.common.api.ErrorCode;
import com.campus.market.common.exception.BusinessException;
import com.campus.market.dto.MessageSendDTO;
import com.campus.market.entity.Conversation;
import com.campus.market.entity.Goods;
import com.campus.market.entity.Message;
import com.campus.market.entity.User;
import com.campus.market.mapper.ConversationMapper;
import com.campus.market.mapper.GoodsImageMapper;
import com.campus.market.mapper.GoodsMapper;
import com.campus.market.mapper.MessageMapper;
import com.campus.market.mapper.UserMapper;
import com.campus.market.service.SensitiveWordService;
import com.campus.market.websocket.WebSocketSessionRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ChatServiceImpl 中测（CHT-02/03/06：类型/接收方/长度/敏感词/商品卡校验、落库+会话派生字段 T5）。
 * 注意：mock 环境无事务同步，registerAfterCommit 内的 WS 推送静默跳过（与生产语义一致）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatServiceImplTest {

    private static final Long SENDER = 1L;
    private static final Long PEER = 2L;

    @Mock ConversationMapper conversationMapper;
    @Mock MessageMapper messageMapper;
    @Mock GoodsMapper goodsMapper;
    @Mock GoodsImageMapper goodsImageMapper;
    @Mock UserMapper userMapper;
    @Mock SensitiveWordService sensitiveWordService;
    @Mock WebSocketSessionRegistry sessionRegistry;

    @InjectMocks ChatServiceImpl service;

    @BeforeAll
    static void initMp() {
        MpTestSupport.initTables(Conversation.class, Message.class, Goods.class, User.class);
    }

    @BeforeEach
    void setUp() {
        lenient().when(sensitiveWordService.findHits(any())).thenReturn(List.of());
        lenient().when(userMapper.selectById(PEER)).thenReturn(new User());
        lenient().when(conversationMapper.selectOne(any())).thenReturn(conversation());
        lenient().when(messageMapper.insert(any(Message.class))).thenReturn(1);
    }

    private Conversation conversation() {
        Conversation c = new Conversation();
        c.setId(70L);
        c.setUser1Id(SENDER);
        c.setUser2Id(PEER);
        c.setUnread1(0);
        c.setUnread2(0);
        return c;
    }

    // ==================== 校验链 ====================

    @Test
    void sendMessage_imageType_notOpen() {
        assertThatThrownBy(() -> service.sendMessage(SENDER, msg("IMAGE", "x.png", PEER)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CHAT_MESSAGE_INVALID));
    }

    @Test
    void sendMessage_unknownType_rejected() {
        assertThatThrownBy(() -> service.sendMessage(SENDER, msg("VIDEO", "x", PEER)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CHAT_MESSAGE_INVALID));
    }

    @Test
    void sendMessage_peerIsSelf_rejected() {
        assertThatThrownBy(() -> service.sendMessage(SENDER, msg("TEXT", "hi", SENDER)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CHAT_MESSAGE_INVALID));
    }

    @Test
    void sendMessage_blankText_rejected() {
        assertThatThrownBy(() -> service.sendMessage(SENDER, msg("TEXT", "  ", PEER)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CHAT_MESSAGE_INVALID));
    }

    @Test
    void sendMessage_over500Chars_rejected() {
        assertThatThrownBy(() -> service.sendMessage(SENDER, msg("TEXT", "长".repeat(501), PEER)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CHAT_MESSAGE_INVALID));
    }

    @Test
    void sendMessage_sensitiveContent_rejected() {
        when(sensitiveWordService.findHits("提供代考服务")).thenReturn(List.of("代考"));
        assertThatThrownBy(() -> service.sendMessage(SENDER, msg("TEXT", "提供代考服务", PEER)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.GOODS_SENSITIVE));
        verify(messageMapper, never()).insert(any(Message.class));
    }

    @Test
    void sendMessage_peerNotFound_rejected() {
        when(userMapper.selectById(PEER)).thenReturn(null);
        assertThatThrownBy(() -> service.sendMessage(SENDER, msg("TEXT", "hi", PEER)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    // ==================== 成功路径 ====================

    @Test
    void sendMessage_text_insertsAndIncrementsPeerUnread() {
        service.sendMessage(SENDER, msg("TEXT", "你好", PEER));

        verify(messageMapper).insert(any(Message.class));
        // T5：接收方是 user2 → unread2+1（inc2=1, inc1=0）
        verify(conversationMapper).updateSummaryAndUnread(eq(70L), eq("你好"), any(), eq(0), eq(1));
    }

    @Test
    void sendMessage_goodsCard_validatesGoods() {
        when(goodsMapper.selectById(9L)).thenReturn(null);
        assertThatThrownBy(() -> service.sendMessage(SENDER, msg("GOODS_CARD", "9", PEER)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.GOODS_NOT_FOUND));
    }

    @Test
    void sendMessage_goodsCard_nonNumericContent_rejected() {
        assertThatThrownBy(() -> service.sendMessage(SENDER, msg("GOODS_CARD", "abc", PEER)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CHAT_MESSAGE_INVALID));
    }

    @Test
    void sendMessage_goodsCard_success_summaryWithPrefix() {
        Goods goods = new Goods();
        goods.setId(9L);
        goods.setTitle("九成新台灯");
        goods.setStatus(Goods.STATUS_ON_SALE);
        when(goodsMapper.selectById(9L)).thenReturn(goods);

        service.sendMessage(SENDER, msg("GOODS_CARD", "9", PEER));

        verify(conversationMapper).updateSummaryAndUnread(eq(70L), eq("[商品] 九成新台灯"), any(), eq(0), eq(1));
    }

    // ==================== 辅助 ====================

    private MessageSendDTO msg(String type, String content, Long peer) {
        MessageSendDTO dto = new MessageSendDTO();
        dto.setMsgType(type);
        dto.setContent(content);
        dto.setPeerUserId(peer);
        return dto;
    }
}
