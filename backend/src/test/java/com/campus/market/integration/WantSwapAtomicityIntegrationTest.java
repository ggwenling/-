package com.campus.market.integration;

import com.campus.market.common.LoginUserTestFactory;
import com.campus.market.entity.Offer;
import com.campus.market.entity.OrderInfo;
import com.campus.market.entity.WantPost;
import com.campus.market.mapper.OfferMapper;
import com.campus.market.mapper.OrderInfoMapper;
import com.campus.market.mapper.WantPostMapper;
import com.campus.market.service.WantPostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T8 原子接受集成测试（Testcontainers MySQL 8，真实事务 + 行锁 + uk_offer_pending）：
 * 接受应约后帖 DEALT、本单 ACCEPTED 回填 order_id、其余待处理全部 REJECTED、PURCHASE 订单生成。
 */
@Transactional
class WantSwapAtomicityIntegrationTest extends AbstractMysqlIntegrationTest {

    private static final Long BUYER = 1L;     // 求购者（帖主）
    private static final Long SELLER_A = 2L;  // 应约者 A（被接受）
    private static final Long SELLER_B = 3L;  // 应约者 B（落选）
    private static final Long POST_ID = 500L;
    private static final Long OFFER_A = 501L;
    private static final Long OFFER_B = 502L;

    @Autowired WantPostService wantPostService;
    @Autowired WantPostMapper wantPostMapper;
    @Autowired OfferMapper offerMapper;
    @Autowired OrderInfoMapper orderInfoMapper;

    @BeforeEach
    void seed() {
        insertUser(BUYER, 100);
        insertUser(SELLER_A, 100);
        insertUser(SELLER_B, 100);

        WantPost post = new WantPost();
        post.setId(POST_ID);
        post.setUserId(BUYER);
        post.setCategoryId(11L);
        post.setTitle("集成-求购高数教材");
        post.setDescription("九成新即可");
        post.setBudget(new BigDecimal("25.00"));
        post.setStatus(WantPost.STATUS_OPEN);
        wantPostMapper.insert(post);

        insertOffer(OFFER_A, SELLER_A, new BigDecimal("22.50"));
        insertOffer(OFFER_B, SELLER_B, new BigDecimal("20.00"));
    }

    @Test
    void acceptOffer_fullAtomicityInRealTransaction() {
        wantPostService.acceptOffer(OFFER_A, LoginUserTestFactory.user(BUYER));

        // 帖子 DEALT
        assertThat(wantPostMapper.selectById(POST_ID).getStatus()).isEqualTo(WantPost.STATUS_DEALT);
        // 被接受应约：ACCEPTED + orderId 回填
        Offer accepted = offerMapper.selectById(OFFER_A);
        assertThat(accepted.getStatus()).isEqualTo(Offer.STATUS_ACCEPTED);
        assertThat(accepted.getOrderId()).isNotNull();
        // 落选应约：REJECTED 且无订单
        Offer rejected = offerMapper.selectById(OFFER_B);
        assertThat(rejected.getStatus()).isEqualTo(Offer.STATUS_REJECTED);
        assertThat(rejected.getOrderId()).isNull();
        // PURCHASE 订单：buyer=求购者、seller=应约者、金额=报价、WAIT_CONFIRM
        OrderInfo order = orderInfoMapper.selectById(accepted.getOrderId());
        assertThat(order.getType()).isEqualTo(OrderInfo.TYPE_PURCHASE);
        assertThat(order.getBuyerId()).isEqualTo(BUYER);
        assertThat(order.getSellerId()).isEqualTo(SELLER_A);
        assertThat(order.getAmount()).isEqualByComparingTo(new BigDecimal("22.50"));
        assertThat(order.getStatus()).isEqualTo(OrderInfo.STATUS_WAIT_CONFIRM);
    }

    // ==================== 种子 ====================

    private void insertUser(Long id, int creditScore) {
        com.campus.market.entity.User user = new com.campus.market.entity.User();
        user.setId(id);
        user.setUsername("iwa" + id);
        user.setPassword("$2a$10$abcdefghijklmnopqrstuv");
        user.setNickname("原子用户" + id);
        user.setCreditScore(creditScore);
        user.setAuthStatus(1);
        user.setStatus(com.campus.market.entity.User.STATUS_NORMAL);
        userMapperInsert(user);
    }

    @Autowired
    private com.campus.market.mapper.UserMapper userMapper;

    private void userMapperInsert(com.campus.market.entity.User user) {
        userMapper.insert(user);
    }

    private void insertOffer(Long id, Long userId, BigDecimal price) {
        Offer offer = new Offer();
        offer.setId(id);
        offer.setWantPostId(POST_ID);
        offer.setUserId(userId);
        offer.setPrice(price);
        offer.setMessage("九成新可面交");
        offer.setStatus(Offer.STATUS_PENDING);
        offerMapper.insert(offer);
    }
}
