package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.market.common.api.ErrorCode;
import com.campus.market.common.api.PageResult;
import com.campus.market.common.exception.BusinessException;
import com.campus.market.dto.MessageSendDTO;
import com.campus.market.entity.Conversation;
import com.campus.market.entity.Goods;
import com.campus.market.entity.GoodsImage;
import com.campus.market.entity.Message;
import com.campus.market.entity.User;
import com.campus.market.mapper.ConversationMapper;
import com.campus.market.mapper.GoodsImageMapper;
import com.campus.market.mapper.GoodsMapper;
import com.campus.market.mapper.MessageMapper;
import com.campus.market.mapper.UserMapper;
import com.campus.market.service.ChatService;
import com.campus.market.service.SensitiveWordService;
import com.campus.market.vo.ChatUnreadVO;
import com.campus.market.vo.ConversationVO;
import com.campus.market.vo.GoodsBriefVO;
import com.campus.market.vo.MessageVO;
import com.campus.market.websocket.WebSocketSessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 私信服务实现（PRD CHT-01~06 / 数据库设计文档 §3.18、§3.19、T5）。
 * 一致性要点：
 * 1. 消息先落库，事务提交后才经 WebSocket 推送（afterCommit），保证"先落库再推送"（PRD §6.4）；
 * 2. 未读数唯一事实依据是 message.read_at（T5）：发消息 unread+1、阅读按"实际置为已读的行数"扣减，
 *    均为原子 UPDATE，并发安全（PRD §6.4 未读数/最后消息/已读位置并发安全）；
 * 3. 会话惰性创建，uk_pair 唯一约束 + DuplicateKeyException 兜底，约定 user1_id &lt; user2_id；
 * 4. 派生字段每日定时全量重建兜底（T5）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final GoodsMapper goodsMapper;
    private final GoodsImageMapper goodsImageMapper;
    private final UserMapper userMapper;
    private final SensitiveWordService sensitiveWordService;
    private final WebSocketSessionRegistry sessionRegistry;

    // ==================== CHT-01 会话列表 ====================

    @Override
    public PageResult<ConversationVO> pageConversations(Long userId, long pageNum, long pageSize) {
        pageSize = Math.min(Math.max(pageSize, 1), 100);
        pageNum = Math.max(pageNum, 1);
        IPage<Conversation> result = conversationMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Conversation>()
                        .and(w -> w.eq(Conversation::getUser1Id, userId).or().eq(Conversation::getUser2Id, userId))
                        .orderByDesc(Conversation::getLastMsgAt)
                        .orderByDesc(Conversation::getId));

        List<Conversation> conversations = result.getRecords();
        Set<Long> peerIds = conversations.stream()
                .map(conv -> peerOf(conv, userId))
                .collect(Collectors.toSet());
        Map<Long, User> userMap = peerIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(peerIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));

        List<ConversationVO> voList = conversations.stream().map(conv -> {
            ConversationVO vo = new ConversationVO();
            vo.setId(conv.getId());
            vo.setPeerUserId(peerOf(conv, userId));
            User peer = userMap.get(vo.getPeerUserId());
            // 对方账号理论上不可物理删除（FK RESTRICT），兜底防空指针
            vo.setPeerNickname(peer != null ? peer.getNickname() : "已注销用户");
            vo.setPeerAvatar(peer != null ? peer.getAvatar() : null);
            vo.setLastMsg(conv.getLastMsg());
            vo.setLastMsgAt(conv.getLastMsgAt());
            vo.setUnreadCount(userId.equals(conv.getUser1Id()) ? conv.getUnread1() : conv.getUnread2());
            vo.setUpdatedAt(conv.getUpdatedAt());
            return vo;
        }).toList();

        PageResult<ConversationVO> voPage = new PageResult<>();
        voPage.setList(voList);
        voPage.setTotal(result.getTotal());
        voPage.setPageNum(result.getCurrent());
        voPage.setPageSize(result.getSize());
        return voPage;
    }

    // ==================== CHT-02/03/06 发送私信 ====================

    @Override
    @Transactional
    public MessageVO sendMessage(Long senderId, MessageSendDTO dto) {
        String msgType = dto.getMsgType() == null ? "" : dto.getMsgType();
        String content = dto.getContent() == null ? "" : dto.getContent().trim();
        if (!Message.TYPE_TEXT.equals(msgType) && !Message.TYPE_GOODS_CARD.equals(msgType)) {
            if (Message.TYPE_IMAGE.equals(msgType)) {
                // CHT-04 为 P2 本期跳过，仅预留类型
                throw new BusinessException(ErrorCode.CHAT_MESSAGE_INVALID, "图片消息暂未开放");
            }
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_INVALID, "不支持的消息类型");
        }
        if (dto.getPeerUserId() == null || dto.getPeerUserId() <= 0 || dto.getPeerUserId().equals(senderId)) {
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_INVALID, "接收方不合法");
        }

        Goods goods = null;
        if (Message.TYPE_TEXT.equals(msgType)) {
            // CHT-02：文本 ≤500 字（DTO 已约束）；CHT-06：发送前过敏感词 DFA，命中复用 GOODS_SENSITIVE 语义
            if (content.isEmpty() || content.length() > 500) {
                throw new BusinessException(ErrorCode.CHAT_MESSAGE_INVALID, "文本消息须为 1~500 字");
            }
            List<String> hits = sensitiveWordService.findHits(content);
            if (!hits.isEmpty()) {
                throw new BusinessException(ErrorCode.GOODS_SENSITIVE, "内容包含敏感词：" + String.join("、", hits));
            }
        } else {
            // CHT-03：商品卡片消息，content=商品 ID，校验商品存在（GoodsMapper 只读，不动 M2 文件）
            long goodsId = parseGoodsId(content);
            if (goodsId <= 0) {
                throw new BusinessException(ErrorCode.CHAT_MESSAGE_INVALID, "商品卡片消息内容须为商品 ID");
            }
            goods = goodsMapper.selectById(goodsId);
            if (goods == null || Goods.STATUS_DELETED.equals(goods.getStatus())) {
                throw new BusinessException(ErrorCode.GOODS_NOT_FOUND);
            }
            content = String.valueOf(goodsId);
        }

        User peer = userMapper.selectById(dto.getPeerUserId());
        if (peer == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "对方用户不存在");
        }

        Conversation conversation = getOrCreateConversation(senderId, dto.getPeerUserId());

        // 1) 落库：read_at=NULL 即未读（T5 唯一事实依据）；createdAt 应用写入，保证落库与推送载荷时间一致
        LocalDateTime now = LocalDateTime.now();
        Message message = new Message();
        message.setConversationId(conversation.getId());
        message.setSenderId(senderId);
        message.setMsgType(msgType);
        message.setContent(content);
        message.setReadAt(null);
        message.setCreatedAt(now);
        messageMapper.insert(message);

        // 2) 同事务原子维护会话派生字段（T5）：接收方 unread+1
        String summary = Message.TYPE_GOODS_CARD.equals(msgType)
                ? "[商品] " + goods.getTitle()
                : content;
        int inc1 = dto.getPeerUserId().equals(conversation.getUser1Id()) ? 1 : 0;
        int inc2 = dto.getPeerUserId().equals(conversation.getUser2Id()) ? 1 : 0;
        conversationMapper.updateSummaryAndUnread(conversation.getId(), summary, now, inc1, inc2);

        // 3) 事务提交后推送（先落库再推送，PRD §6.4）；双方在线都收（发送方多端同步）
        MessageVO vo = toMessageVO(message, toBrief(goods, null));
        pushChatNewAfterCommit(senderId, dto.getPeerUserId(), vo);
        return vo;
    }

    // ==================== CHT-02/05 消息拉取与已读 ====================

    @Override
    @Transactional
    public PageResult<MessageVO> pageMessages(Long userId, Long conversationId, long pageNum, long pageSize) {
        Conversation conversation = requireParticipant(userId, conversationId);
        pageSize = Math.min(Math.max(pageSize, 1), 100);
        pageNum = Math.max(pageNum, 1);
        IPage<Message> result = messageMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, conversation.getId())
                        .orderByDesc(Message::getCreatedAt)
                        .orderByDesc(Message::getId));

        // CHT-05：拉取即已读（与未读数-1 同事务），并给对方推已读回执
        markConversationRead(conversation, userId);
        pushReadReceiptAfterCommit(conversation, userId);

        PageResult<MessageVO> voPage = new PageResult<>();
        voPage.setList(toMessageVOs(result.getRecords()));
        voPage.setTotal(result.getTotal());
        voPage.setPageNum(result.getCurrent());
        voPage.setPageSize(result.getSize());
        return voPage;
    }

    @Override
    @Transactional
    public void markRead(Long userId, Long conversationId) {
        Conversation conversation = requireParticipant(userId, conversationId);
        markConversationRead(conversation, userId);
        pushReadReceiptAfterCommit(conversation, userId);
    }

    @Override
    public ChatUnreadVO countUnread(Long userId) {
        ChatUnreadVO vo = new ChatUnreadVO();
        vo.setConversationCount(conversationMapper.countConversationsWithUnread(userId));
        vo.setMessageCount(conversationMapper.sumUnreadMessages(userId));
        return vo;
    }

    // ==================== T5 定时重建兜底 ====================

    @Override
    @Scheduled(cron = "0 30 3 * * ?")
    public int rebuildDerivedFields() {
        int rows = conversationMapper.rebuildDerivedFields();
        if (rows > 0) {
            log.info("会话派生字段定时重建完成（T5）: {} 个会话", rows);
        }
        return rows;
    }

    // ==================== 私有方法 ====================

    /** 惰性创建会话：uk_pair(user1_id,user2_id) 约定 user1_id<user2_id，并发首聊由唯一约束兜底 */
    private Conversation getOrCreateConversation(Long userIdA, Long userIdB) {
        long user1Id = Math.min(userIdA, userIdB);
        long user2Id = Math.max(userIdA, userIdB);
        Conversation conversation = conversationMapper.selectOne(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getUser1Id, user1Id)
                .eq(Conversation::getUser2Id, user2Id));
        if (conversation != null) {
            return conversation;
        }
        Conversation created = new Conversation();
        created.setUser1Id(user1Id);
        created.setUser2Id(user2Id);
        created.setUnread1(0);
        created.setUnread2(0);
        try {
            conversationMapper.insert(created);
            return created;
        } catch (DuplicateKeyException e) {
            log.debug("并发首次互发，uk_pair 兜底: user1={}, user2={}", user1Id, user2Id);
            return conversationMapper.selectOne(new LambdaQueryWrapper<Conversation>()
                    .eq(Conversation::getUser1Id, user1Id)
                    .eq(Conversation::getUser2Id, user2Id));
        }
    }

    /** 会话存在性 + 双方校验：非双方访问返回 CHAT_FORBIDDEN，会话不存在返回 CHAT_CONV_NOT_FOUND */
    private Conversation requireParticipant(Long userId, Long conversationId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BusinessException(ErrorCode.CHAT_CONV_NOT_FOUND);
        }
        if (!userId.equals(conversation.getUser1Id()) && !userId.equals(conversation.getUser2Id())) {
            throw new BusinessException(ErrorCode.CHAT_FORBIDDEN);
        }
        return conversation;
    }

    /**
     * 已读打点：read_at IS NULL 且非本人发送的消息置为 now（T5 事实依据），
     * 并按"实际置为已读的行数"原子扣减未读（并发阅读不重复扣减，PRD §6.4 并发安全）。
     *
     * @return 本次实际置为已读的行数
     */
    private int markConversationRead(Conversation conversation, Long readerId) {
        int rows = messageMapper.update(null, new LambdaUpdateWrapper<Message>()
                .eq(Message::getConversationId, conversation.getId())
                .isNull(Message::getReadAt)
                .ne(Message::getSenderId, readerId)
                .set(Message::getReadAt, LocalDateTime.now()));
        if (rows > 0) {
            if (readerId.equals(conversation.getUser1Id())) {
                conversationMapper.decreaseUnread1(conversation.getId(), rows);
            } else {
                conversationMapper.decreaseUnread2(conversation.getId(), rows);
            }
        }
        return rows;
    }

    /** 事务提交后向双方在线连接推送新消息（payload: {type:CHAT_NEW, message:VO}） */
    private void pushChatNewAfterCommit(Long senderId, Long receiverId, MessageVO vo) {
        registerAfterCommit(() -> {
            Map<String, Object> payload = Map.of("type", "CHAT_NEW", "message", vo);
            sessionRegistry.sendToUser(senderId, payload);
            sessionRegistry.sendToUser(receiverId, payload);
        });
    }

    /** 事务提交后向阅读方的对方推送已读回执（payload: {type:CHAT_READ, ...}），发送端据此展示已读 */
    private void pushReadReceiptAfterCommit(Conversation conversation, Long readerId) {
        registerAfterCommit(() -> {
            Map<String, Object> payload = Map.of(
                    "type", "CHAT_READ",
                    "conversationId", conversation.getId(),
                    "readerId", readerId);
            sessionRegistry.sendToUser(peerOf(conversation, readerId), payload);
        });
    }

    private void registerAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        }
    }

    private Long peerOf(Conversation conversation, Long userId) {
        return userId.equals(conversation.getUser1Id()) ? conversation.getUser2Id() : conversation.getUser1Id();
    }

    private long parseGoodsId(String content) {
        try {
            return Long.parseLong(content);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private List<MessageVO> toMessageVOs(List<Message> messages) {
        if (messages.isEmpty()) {
            return List.of();
        }
        Map<Long, GoodsBriefVO> goodsMap = loadGoodsBriefs(messages);
        List<MessageVO> voList = new ArrayList<>(messages.size());
        for (Message message : messages) {
            GoodsBriefVO brief = null;
            if (Message.TYPE_GOODS_CARD.equals(message.getMsgType())) {
                long goodsId = parseGoodsId(message.getContent());
                brief = goodsId > 0 ? goodsMap.get(goodsId) : null;
            }
            voList.add(toMessageVO(message, brief));
        }
        return voList;
    }

    private MessageVO toMessageVO(Message message, GoodsBriefVO goods) {
        MessageVO vo = new MessageVO();
        vo.setId(message.getId());
        vo.setConversationId(message.getConversationId());
        vo.setSenderId(message.getSenderId());
        vo.setMsgType(message.getMsgType());
        vo.setContent(message.getContent());
        vo.setReadAt(message.getReadAt());
        vo.setCreatedAt(message.getCreatedAt());
        vo.setGoods(goods);
        return vo;
    }

    /** GOODS_CARD 消息批量取商品快照（标题/价格/封面），GoodsMapper/GoodsImageMapper 只读，不依赖 M2 服务 */
    private Map<Long, GoodsBriefVO> loadGoodsBriefs(List<Message> messages) {
        List<Long> goodsIds = messages.stream()
                .filter(m -> Message.TYPE_GOODS_CARD.equals(m.getMsgType()))
                .map(m -> parseGoodsId(m.getContent()))
                .filter(id -> id > 0)
                .distinct()
                .toList();
        if (goodsIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> coverMap = goodsImageMapper.selectList(new LambdaQueryWrapper<GoodsImage>()
                        .in(GoodsImage::getGoodsId, goodsIds)
                        .orderByAsc(GoodsImage::getSort))
                .stream()
                .collect(Collectors.toMap(GoodsImage::getGoodsId, GoodsImage::getThumbUrl, (a, b) -> a));
        return goodsMapper.selectBatchIds(goodsIds).stream()
                .collect(Collectors.toMap(Goods::getId,
                        goods -> toBrief(goods, coverMap.get(goods.getId())), (a, b) -> a));
    }

    private GoodsBriefVO toBrief(Goods goods, String coverUrl) {
        if (goods == null) {
            return null;
        }
        GoodsBriefVO brief = new GoodsBriefVO();
        brief.setGoodsId(goods.getId());
        brief.setTitle(goods.getTitle());
        brief.setPrice(goods.getPrice());
        brief.setCoverUrl(coverUrl);
        return brief;
    }
}
