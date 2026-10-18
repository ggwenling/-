package com.campus.market.service.impl;

import com.campus.market.common.api.ErrorCode;
import com.campus.market.common.exception.BusinessException;
import com.campus.market.common.util.RedisService;
import com.campus.market.config.AuthProperties;
import com.campus.market.config.CreditProperties;
import com.campus.market.dto.LoginDTO;
import com.campus.market.dto.RefreshDTO;
import com.campus.market.dto.RegisterDTO;
import com.campus.market.dto.SendVerifyCodeDTO;
import com.campus.market.dto.VerifyCheckDTO;
import com.campus.market.entity.User;
import com.campus.market.entity.UserAuth;
import com.campus.market.mapper.UserAuthMapper;
import com.campus.market.mapper.UserMapper;
import com.campus.market.security.JwtUtil;
import com.campus.market.security.LoginUser;
import com.campus.market.service.AuthService;
import com.campus.market.vo.LoginVO;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 认证服务实现（PRD USR-01/02/03、§5.1）。
 * Redis key 遵循 docs/命名规范.md：campus:market:auth:*。
 * 验证码只存 Redis 不落库（T6）；演示模式打印控制台，SMTP 接入为部署期遗留项。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String KEY_LOGIN_FAIL = "campus:market:auth:loginfail:";
    private static final String KEY_LOGIN_IP = "campus:market:auth:loginip:";
    private static final String KEY_CODE = "campus:market:auth:code:";
    private static final String KEY_CODE_LOCK = "campus:market:auth:codelock:";
    private static final String KEY_CODE_DAILY = "campus:market:auth:codedaily:";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserMapper userMapper;
    private final UserAuthMapper userAuthMapper;
    private final RedisService redisService;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties authProperties;

    @Value("${app.demo-mode:true}")
    private boolean demoMode;

    @Override
    public LoginVO register(RegisterDTO dto) {
        if (userMapper.selectByUsername(dto.getUsername()) != null) {
            throw new BusinessException(ErrorCode.AUTH_USERNAME_DUP);
        }
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(dto.getNickname());
        user.setCreditScore(100);
        user.setAuthStatus(User.AUTH_STATUS_UNVERIFIED);
        user.setStatus(User.STATUS_NORMAL);
        userMapper.insert(user);
        log.info("新用户注册：id={} username={}", user.getId(), user.getUsername());
        return issueTokens(user);
    }

    @Override
    public LoginVO login(LoginDTO dto, String clientIp) {
        // 同 IP 限流（PRD §7/§9.2：5 次/分钟/IP）：无论成败均计数，防撞库与暴力枚举
        String ipKey = KEY_LOGIN_IP + (clientIp == null || clientIp.isBlank() ? "unknown" : clientIp);
        long ipHits = redisService.increment(ipKey);
        if (ipHits == 1) {
            // 仅首次自增时补 TTL：increment 与 SET 覆盖写并发会互相覆盖计数（验收 P2②）
            redisService.expire(ipKey, Duration.ofSeconds(authProperties.getLoginIpWindowSeconds()));
        }
        if (ipHits > authProperties.getLoginIpLimit()) {
            log.warn("登录 IP 限流触发：ip={} 窗口内第 {} 次", clientIp, ipHits);
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }

        String failKey = KEY_LOGIN_FAIL + dto.getUsername();
        String fails = redisService.get(failKey);
        if (fails != null && Integer.parseInt(fails) >= authProperties.getLoginFailLimit()) {
            throw new BusinessException(ErrorCode.AUTH_LOGIN_LOCKED);
        }
        User user = userMapper.selectByUsername(dto.getUsername());
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            long count = redisService.increment(failKey);
            if (count == 1) {
                // 首失败起 10 分钟窗口（PRD USR-02）；仅首自增补 TTL，防并发覆盖少计（验收 P2②）
                redisService.expire(failKey, Duration.ofMinutes(authProperties.getLoginLockMinutes()));
            }
            throw new BusinessException(ErrorCode.AUTH_CREDENTIALS_INVALID);
        }
        redisService.delete(failKey);

        User patch = new User();
        patch.setId(user.getId());
        patch.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(patch);

        return issueTokens(user);
    }

    @Override
    public LoginVO refresh(RefreshDTO dto) {
        LoginUser loginUser = parseToken(dto.getRefreshToken());
        if (!JwtUtil.TYPE_REFRESH.equals(loginUser.getTokenType())
                || loginUser.getUserType() != LoginUser.UserType.USER) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        User user = userMapper.selectById(loginUser.getUserId());
        if (user == null) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        return issueTokens(user);
    }

    @Override
    public void logout(String token) {
        // 无状态 JWT：登出即客户端丢弃（PRD USR-02）；token 黑名单为部署期可选项
        log.debug("logout：客户端丢弃 token");
    }

    @Override
    public void sendVerifyCode(SendVerifyCodeDTO dto, Long userId) {
        String email = dto.getCampusEmail().toLowerCase(Locale.ROOT);
        if (authProperties.getEmailWhitelist().stream().noneMatch(email::endsWith)) {
            throw new BusinessException(ErrorCode.AUTH_EMAIL_DOMAIN_FORBIDDEN);
        }
        if (userAuthMapper.selectByUserId(userId) != null) {
            throw new BusinessException(ErrorCode.AUTH_ALREADY_CERTIFIED);
        }
        // 一学号一账号 / 一邮箱一账号：发送前提前校验，给出友好错误码（PRD USR-03）
        UserAuth byStudent = userAuthMapper.selectByStudentNo(dto.getStudentNo());
        if (byStudent != null && !byStudent.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.AUTH_STUDENT_NO_DUP);
        }
        UserAuth byEmail = userAuthMapper.selectByCampusEmail(email);
        if (byEmail != null && !byEmail.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.AUTH_CAMPUS_EMAIL_DUP);
        }
        // 限频：发送间隔 + 每日上限（PRD §5.1）
        if (redisService.hasKey(KEY_CODE_LOCK + email)) {
            throw new BusinessException(ErrorCode.AUTH_CODE_LIMIT);
        }
        long daily = redisService.increment(KEY_CODE_DAILY + email);
        if (daily == 1) {
            redisService.set(KEY_CODE_DAILY + email, "1", Duration.ofHours(24));
        }
        if (daily > authProperties.getCodeDailyLimit()) {
            throw new BusinessException(ErrorCode.AUTH_CODE_LIMIT);
        }
        String code = String.valueOf(100000 + RANDOM.nextInt(900000));
        redisService.set(KEY_CODE + email, code,
                Duration.ofMinutes(authProperties.getCodeTtlMinutes()));
        redisService.set(KEY_CODE_LOCK + email, "1",
                Duration.ofSeconds(authProperties.getCodeSendIntervalSeconds()));
        if (demoMode) {
            log.info("【演示模式】校园邮箱 {} 的验证码：{}（{} 分钟内有效）",
                    email, code, authProperties.getCodeTtlMinutes());
        } else {
            // 生产模式 SMTP 发信为部署期遗留项，当前同样落日志便于联调
            log.warn("SMTP 未配置，验证码落日志：邮箱 {} 验证码 {}（部署期接入邮件服务）", email, code);
        }
    }

    @Override
    public void certify(VerifyCheckDTO dto, Long userId) {
        String email = dto.getCampusEmail().toLowerCase(Locale.ROOT);
        String saved = redisService.get(KEY_CODE + email);
        if (saved == null || !saved.equals(dto.getCode())) {
            throw new BusinessException(ErrorCode.AUTH_CODE_INVALID);
        }
        if (userAuthMapper.selectByUserId(userId) != null) {
            throw new BusinessException(ErrorCode.AUTH_ALREADY_CERTIFIED);
        }
        if (userAuthMapper.selectByStudentNo(dto.getStudentNo()) != null) {
            throw new BusinessException(ErrorCode.AUTH_STUDENT_NO_DUP);
        }
        if (userAuthMapper.selectByCampusEmail(email) != null) {
            throw new BusinessException(ErrorCode.AUTH_CAMPUS_EMAIL_DUP);
        }
        UserAuth userAuth = new UserAuth();
        userAuth.setUserId(userId);
        userAuth.setStudentNo(dto.getStudentNo());
        userAuth.setCampusEmail(email);
        userAuth.setStatus(UserAuth.STATUS_VERIFIED);
        userAuth.setVerifiedAt(LocalDateTime.now());
        userAuthMapper.insert(userAuth);

        User patch = new User();
        patch.setId(userId);
        patch.setAuthStatus(User.AUTH_STATUS_VERIFIED);
        userMapper.updateById(patch);

        redisService.delete(KEY_CODE + email);
        log.info("校园认证通过：userId={} studentNo={}", userId, dto.getStudentNo());
    }

    /** 签发双 token（PRD USR-02）；封禁用户可获取 token 但后续接口被拦截（PRD §4.1） */
    private LoginVO issueTokens(User user) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getId());
        loginUser.setUsername(user.getUsername());
        loginUser.setUserType(LoginUser.UserType.USER);

        LoginVO vo = new LoginVO();
        vo.setToken(jwtUtil.createAccessToken(loginUser));
        vo.setRefreshToken(jwtUtil.createRefreshToken(loginUser));
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setAuthStatus(user.getAuthStatus());
        vo.setBanned(user.getStatus() != null && user.getStatus() == User.STATUS_BANNED);
        vo.setBanReason(user.getBanReason());
        vo.setBannedUntil(user.getBannedUntil() == null ? null : user.getBannedUntil().toString());
        return vo;
    }

    private LoginUser parseToken(String token) {
        try {
            return jwtUtil.parse(token);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
    }
}
