package com.campus.market.service.impl;

import com.campus.market.entity.Notification;
import com.campus.market.mapper.NotificationMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * NotificationServiceImpl 浅测（NTF-01：只写不改，isRead 初始 0）。
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock NotificationMapper notificationMapper;

    @InjectMocks NotificationServiceImpl service;

    @Test
    void push_insertsUnreadNotification() {
        service.push(1L, Notification.TYPE_ORDER, "订单已确认", "内容", "ORDER", 9L);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper).insert(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getType()).isEqualTo(Notification.TYPE_ORDER);
        assertThat(saved.getTitle()).isEqualTo("订单已确认");
        assertThat(saved.getRefType()).isEqualTo("ORDER");
        assertThat(saved.getRefId()).isEqualTo(9L);
        assertThat(saved.getIsRead()).isZero();
    }
}
