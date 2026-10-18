package com.campus.market.service.impl;

import com.campus.market.entity.Notification;
import com.campus.market.mapper.NotificationMapper;
import com.campus.market.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 站内通知写入实现（PRD NTF-01）。只写不改；查询/已读归 M4。
 */
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;

    @Override
    public void push(Long userId, String type, String title, String content, String refType, Long refId) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setRefType(refType);
        notification.setRefId(refId);
        notification.setIsRead(0);
        notificationMapper.insert(notification);
    }
}
