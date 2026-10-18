package com.campus.market.config;

import com.campus.market.entity.Admin;
import com.campus.market.mapper.AdminMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminInitializer 浅测（T12：环境变量注入 + 幂等跳过 + 强制改密标记）。
 */
@ExtendWith(MockitoExtension.class)
class AdminInitializerTest {

    @Mock AdminMapper adminMapper;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks AdminInitializer initializer;

    private void env(String username, String password) throws Exception {
        java.lang.reflect.Field f1 = AdminInitializer.class.getDeclaredField("adminUsername");
        f1.setAccessible(true);
        f1.set(initializer, username);
        java.lang.reflect.Field f2 = AdminInitializer.class.getDeclaredField("adminPassword");
        f2.setAccessible(true);
        f2.set(initializer, password);
    }

    @Test
    void envNotConfigured_skips() throws Exception {
        env("", "");
        initializer.run();
        verify(adminMapper, never()).selectCount(any());
        verify(adminMapper, never()).insert(any(Admin.class));
    }

    @Test
    void adminExists_idempotentSkip() throws Exception {
        env("boss", "Secret!123");
        when(adminMapper.selectCount(any())).thenReturn(1L);

        initializer.run();

        verify(adminMapper, never()).insert(any(Admin.class));
    }

    @Test
    void createsSuperWithMustChangePassword() throws Exception {
        env("boss", "Secret!123");
        when(adminMapper.selectCount(any())).thenReturn(0L);
        when(passwordEncoder.encode("Secret!123")).thenReturn("$2a$encoded");

        initializer.run();

        ArgumentCaptor<Admin> captor = ArgumentCaptor.forClass(Admin.class);
        verify(adminMapper).insert(captor.capture());
        Admin saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("boss");
        assertThat(saved.getPassword()).isEqualTo("$2a$encoded");
        assertThat(saved.getRole()).isEqualTo(Admin.ROLE_SUPER);
        assertThat(saved.getStatus()).isEqualTo(Admin.STATUS_ENABLED);
        assertThat(saved.getMustChangePassword()).isEqualTo(1);   // T12：首次登录强制改密
    }

    @Test
    void usernameOnlyMissing_skips() throws Exception {
        env("boss", "");
        initializer.run();
        verify(adminMapper, never()).insert(any(Admin.class));
    }
}
