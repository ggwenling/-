package com.campus.market.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.market.entity.Admin;
import com.campus.market.mapper.AdminMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 管理员幂等初始化（T12）：
 * - 凭据仅从环境变量 ADMIN_USERNAME / ADMIN_PASSWORD 读取，仓库不存默认明文；
 * - 未配置则跳过；账号已存在则幂等跳过；
 * - 初始账号 must_change_password=1，首次登录强制改密（改密流程归 M6）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements CommandLineRunner {

    private final AdminMapper adminMapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${ADMIN_USERNAME:}")
    private String adminUsername;

    @Value("${ADMIN_PASSWORD:}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        if (!StringUtils.hasText(adminUsername) || !StringUtils.hasText(adminPassword)) {
            log.info("未配置 ADMIN_USERNAME/ADMIN_PASSWORD，跳过管理员初始化（T12）");
            return;
        }
        Long count = adminMapper.selectCount(new LambdaQueryWrapper<Admin>()
                .eq(Admin::getUsername, adminUsername));
        if (count != null && count > 0) {
            log.info("管理员 {} 已存在，初始化幂等跳过（T12）", adminUsername);
            return;
        }
        Admin admin = new Admin();
        admin.setUsername(adminUsername);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setRole(Admin.ROLE_SUPER);
        admin.setStatus(Admin.STATUS_ENABLED);
        admin.setMustChangePassword(1);
        adminMapper.insert(admin);
        log.info("已初始化超级管理员 {}（首次登录须修改密码，T12）", adminUsername);
    }
}
