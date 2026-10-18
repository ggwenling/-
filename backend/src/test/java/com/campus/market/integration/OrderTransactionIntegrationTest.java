package com.campus.market.integration;

import com.campus.market.common.LoginUserTestFactory;
import com.campus.market.entity.Goods;
import com.campus.market.entity.GoodsWant;
import com.campus.market.entity.OrderInfo;
import com.campus.market.entity.User;
import com.campus.market.mapper.GoodsMapper;
import com.campus.market.mapper.GoodsWantMapper;
import com.campus.market.mapper.OrderInfoMapper;
import com.campus.market.mapper.UserMapper;
import com.campus.market.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 订单集成测试（Testcontainers MySQL 8，真实 SQL）：条件 UPDATE 锁定并发兜底、T11 两步取号、
 * goods_want 事实表 T5、超时取消全链（解锁 + 卖方信用 -2 流水）。
 */
@Transactional
class OrderTransactionIntegrationTest extends AbstractMysqlIntegrationTest {

    private static final Long BUYER = 1L;
    private static final Long SELLER = 2L;
    private static final Long GOODS_ID = 900L;

    @Autowired OrderService orderService;
    @Autowired OrderInfoMapper orderInfoMapper;
    @Autowired GoodsMapper goodsMapper;
    @Autowired GoodsWantMapper goodsWantMapper;
    @Autowired UserMapper userMapper;

    @BeforeEach
    void seed() {
        insertUser(BUYER, 1, 100);
        insertUser(SELLER, 1, 100);
        insertGoods(Goods.STATUS_ON_SALE);
    }

    @Test
    void createSaleOrder_realLocks_goodsWantAndOrderNo() {
        OrderInfo order = orderService.createSaleOrder(LoginUserTestFactory.user(BUYER), req(GOODS_ID));

        assertThat(order.getId()).isNotNull();
        assertThat(order.getOrderNo()).startsWith("SH").hasSize(18);   // SH + 8 位日期 + 6 位序号
        assertThat(order.getStatus()).isEqualTo(OrderInfo.STATUS_WAIT_CONFIRM);

        // 商品真实锁定
        assertThat(goodsMapper.selectById(GOODS_ID).getStatus()).isEqualTo(Goods.STATUS_IN_TRANSACTION);
        // T5：想要事实行 + 计数原子 +1
        assertThat(goodsWantMapper.selectCount(null)).isEqualTo(1L);
        assertThat(goodsMapper.selectById(GOODS_ID).getWantCount()).isEqualTo(1L);
    }

    @Test
    void concurrentSecondOrderOnLockedGoods_rejected() {
        orderService.createSaleOrder(LoginUserTestFactory.user(BUYER), req(GOODS_ID));
        // 第二次下单（另一买家）：条件 UPDATE rows=0 → 40907
        insertUser(3L, 1, 100);
        assertThatThrownBy(() -> orderService.createSaleOrder(LoginUserTestFactory.user(3L), req(GOODS_ID)))
                .isInstanceOf(com.campus.market.common.exception.BusinessException.class);
        assertThat(orderInfoMapper.selectCount(null)).isEqualTo(1L);
    }

    @Test
    void timeoutCancel_unlocksGoodsAndPenalizesSeller() {
        // 预置已完成一笔（锁商品），再预置一笔 49h 前的 WAIT_CONFIRM 订单
        orderService.createSaleOrder(LoginUserTestFactory.user(BUYER), req(GOODS_ID));
        OrderInfo expired = orderInfoMapper.selectList(null).get(0);
        // 把 createdAt 拨回 49h 前（模拟超时）
        orderInfoMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<OrderInfo>()
                .eq(OrderInfo::getId, expired.getId())
                .set(OrderInfo::getCreatedAt, LocalDateTime.now().minusHours(49)));

        int cancelled = orderService.autoCancelTimeoutOrders();

        assertThat(cancelled).isEqualTo(1);
        assertThat(orderInfoMapper.selectById(expired.getId()).getStatus()).isEqualTo(OrderInfo.STATUS_CANCELLED);
        assertThat(orderInfoMapper.selectById(expired.getId()).getCancelledBy())
                .isEqualTo(OrderInfo.CANCELLED_BY_TIMEOUT);
        // 商品解锁
        assertThat(goodsMapper.selectById(GOODS_ID).getStatus()).isEqualTo(Goods.STATUS_ON_SALE);
        // 卖方信用 100 → 98，credit_log 一条 CANCEL_TIMEOUT
        assertThat(userMapper.selectById(SELLER).getCreditScore()).isEqualTo(98);
    }

    // ==================== 种子（直连 mapper，字段与 schema NOT NULL 对齐） ====================

    private void insertUser(Long id, int authStatus, int creditScore) {
        User user = new User();
        user.setId(id);
        user.setUsername("itu" + id);
        user.setPassword("$2a$10$abcdefghijklmnopqrstuv");
        user.setNickname("集成用户" + id);
        user.setCreditScore(creditScore);
        user.setAuthStatus(authStatus);
        user.setStatus(User.STATUS_NORMAL);
        userMapper.insert(user);
    }

    private void insertGoods(String status) {
        Goods goods = new Goods();
        goods.setId(GOODS_ID);
        goods.setUserId(SELLER);
        goods.setCategoryId(11L);
        goods.setTitle("集成测试台灯");
        goods.setDescription("九成新");
        goods.setPrice(new BigDecimal("45.00"));
        goods.setConditionLevel(2);
        goods.setStatus(status);
        goods.setViewCount(0L);
        goods.setWantCount(0L);
        goods.setFavoriteCount(0L);
        goods.setHeatScore(0);
        goodsMapper.insert(goods);
    }

    private com.campus.market.dto.OrderCreateDTO req(Long goodsId) {
        com.campus.market.dto.OrderCreateDTO dto = new com.campus.market.dto.OrderCreateDTO();
        dto.setGoodsId(goodsId);
        return dto;
    }
}
