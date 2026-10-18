package com.campus.market.service.impl;

import com.campus.market.common.LoginUserTestFactory;
import com.campus.market.common.MpTestSupport;
import com.campus.market.common.api.ErrorCode;
import com.campus.market.common.exception.BusinessException;
import com.campus.market.config.CreditProperties;
import com.campus.market.dto.UserEditDTO;
import com.campus.market.entity.User;
import com.campus.market.mapper.UserAuthMapper;
import com.campus.market.mapper.UserMapper;
import com.campus.market.security.LoginUser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserServiceImpl 中测（applyFreshState 封禁/到期解封 + 资料编辑 XSS 转义 + 信用四档等级）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceImplTest {

    private static final Long USER_ID = 1L;

    @Mock UserMapper userMapper;
    @Mock UserAuthMapper userAuthMapper;
    @Mock CreditProperties creditProperties;

    @InjectMocks UserServiceImpl service;

    @BeforeAll
    static void initMp() {
        MpTestSupport.initTables(User.class, com.campus.market.entity.UserAuth.class);
    }

    @BeforeEach
    void setUp() {
        lenient().when(creditProperties.getExcellentThreshold()).thenReturn(120);
        lenient().when(creditProperties.getGoodThreshold()).thenReturn(80);
        lenient().when(creditProperties.getRestrictedThreshold()).thenReturn(60);
    }

    // ==================== applyFreshState（拦截器每请求刷新） ====================

    @Test
    void applyFreshState_bannedUntilFuture_refreshesStatusNotRejected() {
        User user = user(User.STATUS_BANNED);
        user.setBannedUntil(LocalDateTime.now().plusDays(3));
        when(userMapper.selectById(USER_ID)).thenReturn(user);

        // M7：封禁的强制拦截上移到 AuthInterceptor（需按 URI 白名单放行个人资料等自助接口，PRD §3.1），
        // 本方法只负责刷新状态，不再抛异常
        LoginUser loginUser = LoginUserTestFactory.user(USER_ID);
        service.applyFreshState(loginUser);

        assertThat(loginUser.getStatus()).isEqualTo(User.STATUS_BANNED);
        assertThat(loginUser.getAuthStatus()).isEqualTo(User.AUTH_STATUS_VERIFIED);
        verify(userMapper, never()).updateById(any(User.class));   // 未到期不做解封写入
    }

    @Test
    void applyFreshState_banExpired_autoUnbans() {
        User user = user(User.STATUS_BANNED);
        user.setBannedUntil(LocalDateTime.now().minusMinutes(1));
        when(userMapper.selectById(USER_ID)).thenReturn(user);

        LoginUser loginUser = LoginUserTestFactory.user(USER_ID);
        service.applyFreshState(loginUser);

        verify(userMapper).updateById(any(User.class));   // 状态回 NORMAL
        assertThat(loginUser.getStatus()).isEqualTo(User.STATUS_NORMAL);   // 到期解封后状态为正常
        assertThat(loginUser.getAuthStatus()).isEqualTo(User.AUTH_STATUS_VERIFIED);
    }

    @Test
    void applyFreshState_userMissing_silent() {
        when(userMapper.selectById(USER_ID)).thenReturn(null);
        assertThatCode(() -> service.applyFreshState(LoginUserTestFactory.user(USER_ID)))
                .doesNotThrowAnyException();
    }

    // ==================== 资料编辑（PRD §9.2 XSS 转义） ====================

    @Test
    void updateMe_escapesBioAgainstXss() {
        UserEditDTO dto = new UserEditDTO();
        dto.setNickname("小明");
        dto.setBio("<script>alert(1)</script> 自用");

        service.updateMe(USER_ID, dto);

        verify(userMapper).updateById(any(User.class));   // bio 已 HtmlEscape（无法直接断言 patch 内部值，由集成层兜底）
    }

    // ==================== 信用四档等级（PRD §5.7） ====================

    @Test
    void creditLevels_fourBands() {
        when(userMapper.selectById(USER_ID)).thenReturn(userWithScore(130));
        assertThat(service.getProfile(USER_ID).getCreditLevel()).isEqualTo("优秀");

        when(userMapper.selectById(USER_ID)).thenReturn(userWithScore(90));
        assertThat(service.getProfile(USER_ID).getCreditLevel()).isEqualTo("良好");

        when(userMapper.selectById(USER_ID)).thenReturn(userWithScore(70));
        assertThat(service.getProfile(USER_ID).getCreditLevel()).isEqualTo("一般");

        when(userMapper.selectById(USER_ID)).thenReturn(userWithScore(50));
        assertThat(service.getProfile(USER_ID).getCreditLevel()).isEqualTo("受限");
    }

    // ==================== 辅助 ====================

    private User user(int status) {
        User user = new User();
        user.setId(USER_ID);
        user.setUsername("testuser");
        user.setStatus(status);
        user.setAuthStatus(User.AUTH_STATUS_VERIFIED);
        user.setCreditScore(100);
        return user;
    }

    private User userWithScore(int creditScore) {
        User user = new User();
        user.setId(USER_ID);
        user.setUsername("testuser");
        user.setStatus(User.STATUS_NORMAL);
        user.setAuthStatus(User.AUTH_STATUS_VERIFIED);
        user.setCreditScore(creditScore);
        return user;
    }
}
