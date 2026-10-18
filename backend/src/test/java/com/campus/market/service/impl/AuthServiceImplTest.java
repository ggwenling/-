package com.campus.market.service.impl;

import com.campus.market.common.MpTestSupport;
import com.campus.market.common.api.ErrorCode;
import com.campus.market.common.exception.BusinessException;
import com.campus.market.common.util.RedisService;
import com.campus.market.config.AuthProperties;
import com.campus.market.config.JwtProperties;
import com.campus.market.dto.LoginDTO;
import com.campus.market.dto.RegisterDTO;
import com.campus.market.dto.SendVerifyCodeDTO;
import com.campus.market.dto.VerifyCheckDTO;
import com.campus.market.entity.User;
import com.campus.market.entity.UserAuth;
import com.campus.market.mapper.UserAuthMapper;
import com.campus.market.mapper.UserMapper;
import com.campus.market.security.JwtUtil;
import com.campus.market.security.LoginUser;
import com.campus.market.vo.LoginVO;
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

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthServiceImpl 深测（USR-01/02/03：注册重复、登录锁定 5 次窗口、双 token 签发、认证码校验与占位校验）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceImplTest {

    private static final String KEY_LOGIN_FAIL = "campus:market:auth:loginfail:";
    private static final String KEY_CODE = "campus:market:auth:code:";

    @Mock UserMapper userMapper;
    @Mock UserAuthMapper userAuthMapper;
    @Mock RedisService redisService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuthProperties authProperties;

    @Spy
    JwtUtil jwtUtil = new JwtUtil(jwtProps());

    @InjectMocks AuthServiceImpl service;

    @BeforeAll
    static void initMp() {
        MpTestSupport.initTables(User.class, UserAuth.class);
    }

    private static JwtProperties jwtProps() {
        JwtProperties props = new JwtProperties();
        props.setSecret("test-only-secret-key-0123456789abcdef");
        return props;
    }

    @BeforeEach
    void setUp() {
        lenient().when(authProperties.getLoginFailLimit()).thenReturn(5);
        lenient().when(authProperties.getLoginLockMinutes()).thenReturn(10);
        lenient().when(authProperties.getLoginIpLimit()).thenReturn(5);
        lenient().when(authProperties.getLoginIpWindowSeconds()).thenReturn(60);
        lenient().when(authProperties.getEmailWhitelist()).thenReturn(List.of("@stu.example.edu.cn"));
        lenient().when(authProperties.getCodeTtlMinutes()).thenReturn(10);
        lenient().when(authProperties.getCodeSendIntervalSeconds()).thenReturn(60);
        lenient().when(authProperties.getCodeDailyLimit()).thenReturn(10);
        lenient().when(redisService.get(anyString())).thenReturn(null);
        lenient().when(redisService.increment(anyString())).thenReturn(1L);
    }

    // ==================== 注册 / 登录 ====================

    @Test
    void register_usernameDup_rejected() {
        when(userMapper.selectByUsername("neo")).thenReturn(new User());
        assertThatThrownBy(() -> service.register(register("neo")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_USERNAME_DUP));
    }

    @Test
    void register_success_initializesCredit100AndUnverified() {
        when(userMapper.selectByUsername("neo")).thenReturn(null);
        when(passwordEncoder.encode("Passw0rd!")).thenReturn("$2a$hash");

        LoginVO vo = service.register(register("neo"));

        verify(userMapper).insert(any(User.class));
        assertThat(vo.getAuthStatus()).isEqualTo(User.AUTH_STATUS_UNVERIFIED);
        assertThat(vo.getToken()).isNotBlank();
        assertThat(vo.getRefreshToken()).isNotBlank();
    }

    @Test
    void login_failCountReached_locked() {
        when(redisService.get(KEY_LOGIN_FAIL + "neo")).thenReturn("5");

        assertThatThrownBy(() -> service.login(login("neo", "bad"), "127.0.0.1"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_LOGIN_LOCKED));
    }

    @Test
    void login_ipOverLimit_rejectedWithTooManyRequests() {
        // 同 IP 窗口内第 6 次（阈值 5）→ 42900，且不再进入用户名锁定/凭据校验（PRD §7/§9.2）
        when(redisService.increment("campus:market:auth:loginip:10.1.1.9")).thenReturn(6L);

        assertThatThrownBy(() -> service.login(login("neo", "bad"), "10.1.1.9"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TOO_MANY_REQUESTS));
        verify(userMapper, never()).selectByUsername(anyString());
    }

    @Test
    void login_ipFirstHit_setsWindowTtl() {
        when(redisService.increment("campus:market:auth:loginip:10.1.1.9")).thenReturn(1L);
        when(userMapper.selectByUsername("neo")).thenReturn(null);
        when(passwordEncoder.matches(any(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.login(login("neo", "bad"), "10.1.1.9"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_CREDENTIALS_INVALID));

        verify(redisService).set(eq("campus:market:auth:loginip:10.1.1.9"), eq("1"),
                eq(Duration.ofSeconds(60)));
    }

    @Test
    void login_wrongPassword_incrementsFailCounterWithTtl() {
        User user = user(1L, "neo", "$2a$hash", User.STATUS_NORMAL);
        when(userMapper.selectByUsername("neo")).thenReturn(user);
        when(passwordEncoder.matches(any(), anyString())).thenReturn(false);
        when(redisService.increment(KEY_LOGIN_FAIL + "neo")).thenReturn(1L);

        assertThatThrownBy(() -> service.login(login("neo", "bad"), "127.0.0.1"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_CREDENTIALS_INVALID));

        verify(redisService).set(eq(KEY_LOGIN_FAIL + "neo"), eq("1"),
                eq(Duration.ofMinutes(10)));   // 10 分钟锁定窗口（PRD USR-02）
    }

    @Test
    void login_success_clearsFailCounter_andUpdatesLastLogin() {
        User user = user(1L, "neo", "$2a$hash", User.STATUS_NORMAL);
        when(userMapper.selectByUsername("neo")).thenReturn(user);
        when(passwordEncoder.matches("Passw0rd!", "$2a$hash")).thenReturn(true);

        LoginVO vo = service.login(login("neo", "Passw0rd!"), "127.0.0.1");

        verify(redisService).delete(KEY_LOGIN_FAIL + "neo");
        verify(userMapper).updateById(any(User.class));
        assertThat(vo.getUserId()).isEqualTo(1L);
        assertThat(vo.getBanned()).isFalse();
    }

    @Test
    void refresh_withAccessToken_rejected() {
        // access token 不能当 refresh 用（TYPE 口径校验）
        LoginUser access = new LoginUser();
        access.setUserId(1L);
        access.setUserType(LoginUser.UserType.USER);
        access.setTokenType(JwtUtil.TYPE_ACCESS);
        String accessToken = jwtUtil.createAccessToken(access);

        com.campus.market.dto.RefreshDTO dto = new com.campus.market.dto.RefreshDTO();
        dto.setRefreshToken(accessToken);
        assertThatThrownBy(() -> service.refresh(dto))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TOKEN_INVALID));
    }

    // ==================== 校园认证 ====================

    @Test
    void sendVerifyCode_nonCampusDomain_rejected() {
        SendVerifyCodeDTO dto = verifyCode("neo@evil.com", "2025001");
        assertThatThrownBy(() -> service.sendVerifyCode(dto, 1L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_EMAIL_DOMAIN_FORBIDDEN));
    }

    @Test
    void sendVerifyCode_alreadyCertified_rejected() {
        when(userAuthMapper.selectByUserId(1L)).thenReturn(new UserAuth());
        SendVerifyCodeDTO dto = verifyCode("neo@stu.example.edu.cn", "2025001");
        assertThatThrownBy(() -> service.sendVerifyCode(dto, 1L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_ALREADY_CERTIFIED));
    }

    @Test
    void sendVerifyCode_studentNoTakenByOther_rejected() {
        UserAuth taken = new UserAuth();
        taken.setUserId(99L);
        when(userAuthMapper.selectByUserId(1L)).thenReturn(null);
        when(userAuthMapper.selectByStudentNo("2025001")).thenReturn(taken);
        SendVerifyCodeDTO dto = verifyCode("neo@stu.example.edu.cn", "2025001");

        assertThatThrownBy(() -> service.sendVerifyCode(dto, 1L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_STUDENT_NO_DUP));
    }

    @Test
    void sendVerifyCode_withinInterval_limited() {
        when(userAuthMapper.selectByUserId(1L)).thenReturn(null);
        when(userAuthMapper.selectByStudentNo("2025001")).thenReturn(null);
        when(userAuthMapper.selectByCampusEmail("neo@stu.example.edu.cn")).thenReturn(null);
        when(redisService.hasKey("campus:market:auth:codelock:neo@stu.example.edu.cn")).thenReturn(true);
        SendVerifyCodeDTO dto = verifyCode("neo@stu.example.edu.cn", "2025001");

        assertThatThrownBy(() -> service.sendVerifyCode(dto, 1L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_CODE_LIMIT));
    }

    @Test
    void sendVerifyCode_success_storesCodeWithTtl() {
        when(userAuthMapper.selectByUserId(1L)).thenReturn(null);
        when(userAuthMapper.selectByStudentNo("2025001")).thenReturn(null);
        when(userAuthMapper.selectByCampusEmail("neo@stu.example.edu.cn")).thenReturn(null);
        when(redisService.hasKey(anyString())).thenReturn(false);
        SendVerifyCodeDTO dto = verifyCode("neo@stu.example.edu.cn", "2025001");

        service.sendVerifyCode(dto, 1L);

        verify(redisService).set(startsWith(KEY_CODE), org.mockito.ArgumentMatchers.matches("\\d{6}"),
                eq(Duration.ofMinutes(10)));
    }

    @Test
    void certify_wrongCode_rejected() {
        when(redisService.get(KEY_CODE + "neo@stu.example.edu.cn")).thenReturn("123456");
        VerifyCheckDTO dto = certify("neo@stu.example.edu.cn", "2025001", "000000");

        assertThatThrownBy(() -> service.certify(dto, 1L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_CODE_INVALID));
    }

    @Test
    void certify_success_setsVerifiedAndDeletesCode() {
        when(redisService.get(KEY_CODE + "neo@stu.example.edu.cn")).thenReturn("123456");
        when(userAuthMapper.selectByUserId(1L)).thenReturn(null);
        when(userAuthMapper.selectByStudentNo("2025001")).thenReturn(null);
        when(userAuthMapper.selectByCampusEmail("neo@stu.example.edu.cn")).thenReturn(null);
        VerifyCheckDTO dto = certify("neo@stu.example.edu.cn", "2025001", "123456");

        service.certify(dto, 1L);

        verify(userAuthMapper).insert(any(UserAuth.class));
        verify(userMapper).updateById(any(User.class));   // authStatus → 1
        verify(redisService).delete(KEY_CODE + "neo@stu.example.edu.cn");
    }

    // ==================== 辅助 ====================

    private RegisterDTO register(String username) {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername(username);
        dto.setPassword("Passw0rd!");
        dto.setNickname("测试用户");
        return dto;
    }

    private LoginDTO login(String username, String password) {
        LoginDTO dto = new LoginDTO();
        dto.setUsername(username);
        dto.setPassword(password);
        return dto;
    }

    private SendVerifyCodeDTO verifyCode(String email, String studentNo) {
        SendVerifyCodeDTO dto = new SendVerifyCodeDTO();
        dto.setCampusEmail(email);
        dto.setStudentNo(studentNo);
        return dto;
    }

    private VerifyCheckDTO certify(String email, String studentNo, String code) {
        VerifyCheckDTO dto = new VerifyCheckDTO();
        dto.setCampusEmail(email);
        dto.setStudentNo(studentNo);
        dto.setCode(code);
        return dto;
    }

    private User user(Long id, String username, String password, int status) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setPassword(password);
        user.setNickname("测试用户");
        user.setStatus(status);
        user.setAuthStatus(User.AUTH_STATUS_UNVERIFIED);
        return user;
    }
}
