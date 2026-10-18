package com.campus.market.service.impl;

import com.campus.market.common.LoginUserTestFactory;
import com.campus.market.common.MpTestSupport;
import com.campus.market.common.api.ErrorCode;
import com.campus.market.common.exception.BusinessException;
import com.campus.market.config.JwtProperties;
import com.campus.market.entity.Admin;
import com.campus.market.mapper.AdminMapper;
import com.campus.market.security.JwtUtil;
import com.campus.market.security.LoginUser;
import com.campus.market.service.OperationLogService;
import com.campus.market.vo.AdminLoginVO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminServiceImpl 深测（ADM-01/T12：登录、角色大小写口径、强制改密、账号管理与审计）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminServiceImplTest {

    private static final Long ADMIN_ID = 900L;

    @Mock AdminMapper adminMapper;
    @Mock PasswordEncoder passwordEncoder;
    @Mock OperationLogService operationLogService;

    @Spy
    JwtUtil jwtUtil = new JwtUtil(jwtProps());

    @InjectMocks AdminServiceImpl service;

    @BeforeAll
    static void initMp() {
        MpTestSupport.initTables(Admin.class);
    }

    private static JwtProperties jwtProps() {
        JwtProperties props = new JwtProperties();
        props.setSecret("test-only-secret-key-0123456789abcdef");
        return props;
    }

    @BeforeEach
    void setUp() {
        lenient().when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        lenient().when(passwordEncoder.encode(anyString())).thenReturn("$2a$encoded");
        lenient().when(adminMapper.selectCount(any())).thenReturn(0L);
    }

    private Admin admin(String role, int status, int mustChange) {
        Admin admin = new Admin();
        admin.setId(ADMIN_ID);
        admin.setUsername("boss");
        admin.setPassword("$2a$hash");
        admin.setRealName("超管");
        admin.setRole(role);
        admin.setStatus(status);
        admin.setMustChangePassword(mustChange);
        return admin;
    }

    private LoginUser operator() {
        LoginUser user = LoginUserTestFactory.admin("super");
        user.setMustChangePassword(0);
        return user;
    }

    /** userId 对齐测试常量（工厂默认带 hash，需覆盖） */
    private LoginUser adminUser() {
        LoginUser user = LoginUserTestFactory.admin("super");
        user.setUserId(ADMIN_ID);
        return user;
    }

    // ==================== 登录 ====================

    @Test
    void login_wrongPassword_rejected() {
        when(adminMapper.selectOne(any())).thenReturn(admin("SUPER", Admin.STATUS_ENABLED, 0));
        when(passwordEncoder.matches(eq("bad"), anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.login("boss", "bad", "127.0.0.1"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_CREDENTIALS_INVALID));
    }

    @Test
    void login_disabledAccount_rejected() {
        when(adminMapper.selectOne(any())).thenReturn(admin("SUPER", Admin.STATUS_DISABLED, 0));

        assertThatThrownBy(() -> service.login("boss", "Secret!123", "127.0.0.1"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADMIN_DISABLED));
    }

    @Test
    void login_success_roleLowercaseInJwt_andMustChangeFlag() {
        when(adminMapper.selectOne(any())).thenReturn(admin("SUPER", Admin.STATUS_ENABLED, 1));

        AdminLoginVO vo = service.login("boss", "Secret!123", "127.0.0.1");

        assertThat(vo.getRole()).isEqualTo("super");        // 大小写口径：DB 大写 → JWT/VO 小写
        assertThat(vo.getMustChangePassword()).isTrue();    // T12 透出
        assertThat(vo.getToken()).isNotBlank();
        verify(adminMapper).updateById(any(Admin.class));   // lastLoginAt 刷新
    }

    // ==================== 修改密码 ====================

    @Test
    void changePassword_oldPasswordWrong_rejected() {
        when(adminMapper.selectById(ADMIN_ID)).thenReturn(admin("SUPER", Admin.STATUS_ENABLED, 1));
        when(passwordEncoder.matches(eq("wrong"), anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(ADMIN_ID, "wrong", "NewPass123"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADM_OLD_PASSWORD_WRONG));
    }

    @Test
    void changePassword_success_clearsMustChangeFlag_andAudits() {
        when(adminMapper.selectById(ADMIN_ID)).thenReturn(admin("SUPER", Admin.STATUS_ENABLED, 1));

        service.changePassword(ADMIN_ID, "Secret!123", "NewPass123");

        verify(adminMapper).updateById(any(Admin.class));   // 新密码 + mustChangePassword=0
        verify(operationLogService).record(eq(ADMIN_ID), eq("boss"),
                eq(com.campus.market.entity.OperationLog.ACTION_RESET_PASSWORD), eq("ADMIN"),
                eq(ADMIN_ID), anyString(), anyString());
    }

    @Test
    void changePassword_tooShort_rejected() {
        when(adminMapper.selectById(ADMIN_ID)).thenReturn(admin("SUPER", Admin.STATUS_ENABLED, 1));

        assertThatThrownBy(() -> service.changePassword(ADMIN_ID, "Secret!123", "short"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADM_PARAM_INVALID));
    }

    // ==================== 账号管理（SUPER） ====================

    @Test
    void create_usernameDup_rejected() {
        when(adminMapper.selectCount(any())).thenReturn(1L);
        assertThatThrownBy(() -> service.create("boss", "Secret!123", "姓名", "super", operator()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADMIN_USERNAME_DUP));
    }

    @Test
    void create_invalidRole_rejected() {
        assertThatThrownBy(() -> service.create("neo", "Secret!123", "姓名", "hacker", operator()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADM_PARAM_INVALID));
    }

    @Test
    void create_success_roleUppercaseInDb_mustChangeOne() {
        when(adminMapper.insert(any(Admin.class))).thenAnswer(inv -> {
            inv.getArgument(0, Admin.class).setId(901L);
            return 1;
        });

        Long id = service.create("neo", "Secret!123", "审计员", "auditor", operator());

        assertThat(id).isEqualTo(901L);
        verify(adminMapper).insert(org.mockito.ArgumentMatchers.argThat((Admin a) ->
                "AUDITOR".equals(a.getRole())                    // DB 大写口径
                        && Integer.valueOf(1).equals(a.getMustChangePassword())));
        verify(operationLogService).record(any(), any(), eq(com.campus.market.entity.OperationLog.ACTION_ADMIN_CREATE),
                eq("ADMIN"), any(), anyString(), anyString());
    }

    @Test
    void update_disable_writesDisableAudit() {
        when(adminMapper.selectById(ADMIN_ID)).thenReturn(admin("SUPER", Admin.STATUS_ENABLED, 0));

        service.update(ADMIN_ID, null, null, Admin.STATUS_DISABLED, operator());

        verify(operationLogService).record(any(), any(),
                eq(com.campus.market.entity.OperationLog.ACTION_ADMIN_DISABLE),
                eq("ADMIN"), eq(ADMIN_ID), anyString(), anyString());
    }

    @Test
    void resetPassword_forcesChangeFlag() {
        when(adminMapper.selectById(ADMIN_ID)).thenReturn(admin("SUPER", Admin.STATUS_ENABLED, 0));

        service.resetPassword(ADMIN_ID, "NewPass123", operator());

        verify(adminMapper).updateById(org.mockito.ArgumentMatchers.argThat((Admin a) ->
                Integer.valueOf(1).equals(a.getMustChangePassword())));
    }

    // ==================== applyFreshState（拦截器每请求刷新） ====================

    @Test
    void applyFreshState_disabled_rejected() {
        when(adminMapper.selectById(ADMIN_ID)).thenReturn(admin("SUPER", Admin.STATUS_DISABLED, 0));
        LoginUser adminUser = adminUser();

        assertThatThrownBy(() -> service.applyFreshState(adminUser))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADMIN_DISABLED));
    }

    @Test
    void applyFreshState_normal_refreshesRoleAndFlag() {
        when(adminMapper.selectById(ADMIN_ID)).thenReturn(admin("OPERATOR", Admin.STATUS_ENABLED, 1));
        LoginUser adminUser = adminUser();

        service.applyFreshState(adminUser);

        assertThat(adminUser.getAdminRole()).isEqualTo("operator");   // DB 最新角色覆盖 token 角色
        assertThat(adminUser.getMustChangePassword()).isEqualTo(1);
    }

    @Test
    void applyFreshState_adminMissing_rejected() {
        when(adminMapper.selectById(ADMIN_ID)).thenReturn(null);
        LoginUser adminUser = LoginUserTestFactory.admin("super");
        adminUser.setUserId(ADMIN_ID);

        assertThatThrownBy(() -> service.applyFreshState(adminUser))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADMIN_NOT_FOUND));
    }
}
