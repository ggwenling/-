package com.campus.market.integration;

import com.campus.market.entity.CreditLog;
import com.campus.market.entity.User;
import com.campus.market.mapper.CreditLogMapper;
import com.campus.market.mapper.UserMapper;
import com.campus.market.service.CreditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 信用引擎集成测试（Testcontainers MySQL 8，真实 SQL）：selectUserForUpdate 行锁、clamp 0~150、
 * credit_log 幂等键（同 reason+ref 不重复）真实落库。
 */
@Transactional
class CreditLockIntegrationTest extends AbstractMysqlIntegrationTest {

    private static final Long USER_ID = 1L;
    private static final Long ORDER_REF = 900L;

    @Autowired CreditService creditService;
    @Autowired CreditLogMapper creditLogMapper;
    @Autowired UserMapper userMapper;

    @BeforeEach
    void seed() {
        User user = new User();
        user.setId(USER_ID);
        user.setUsername("icl1");
        user.setPassword("$2a$10$abcdefghijklmnopqrstuv");
        user.setNickname("信用用户");
        user.setCreditScore(100);
        user.setAuthStatus(1);
        user.setStatus(User.STATUS_NORMAL);
        userMapper.insert(user);
    }

    @Test
    void addCredit_writesLogAndUpdatesScore() {
        creditService.addCredit(USER_ID, CreditLog.REASON_ORDER_COMPLETE, CreditLog.REF_TYPE_ORDER, ORDER_REF);

        assertThat(userMapper.selectById(USER_ID).getCreditScore()).isEqualTo(102);
        assertThat(creditLogMapper.selectCount(null)).isEqualTo(1L);
    }

    @Test
    void duplicateEvent_idempotent_noSecondLog() {
        creditService.addCredit(USER_ID, CreditLog.REASON_ORDER_COMPLETE, CreditLog.REF_TYPE_ORDER, ORDER_REF);
        creditService.addCredit(USER_ID, CreditLog.REASON_ORDER_COMPLETE, CreditLog.REF_TYPE_ORDER, ORDER_REF);

        assertThat(userMapper.selectById(USER_ID).getCreditScore()).isEqualTo(102);   // 不重复 +2
        assertThat(creditLogMapper.selectCount(null)).isEqualTo(1L);
    }

    @Test
    void clampAtUpperBound_realUpdate() {
        // 149 + 2 → clamp 150
        User patch = new User();
        patch.setId(USER_ID);
        patch.setCreditScore(149);
        userMapper.updateById(patch);

        creditService.addCredit(USER_ID, CreditLog.REASON_ORDER_COMPLETE, CreditLog.REF_TYPE_ORDER, ORDER_REF);

        assertThat(userMapper.selectById(USER_ID).getCreditScore()).isEqualTo(150);
    }

    @Test
    void differentRefId_bothLogged() {
        creditService.addCredit(USER_ID, CreditLog.REASON_ORDER_COMPLETE, CreditLog.REF_TYPE_ORDER, ORDER_REF);
        creditService.addCredit(USER_ID, CreditLog.REASON_ORDER_COMPLETE, CreditLog.REF_TYPE_ORDER, ORDER_REF + 1);

        assertThat(userMapper.selectById(USER_ID).getCreditScore()).isEqualTo(104);   // 两个独立事件
        assertThat(creditLogMapper.selectCount(null)).isEqualTo(2L);
    }
}
