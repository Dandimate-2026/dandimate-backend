package com.dandimate.durooduroo.notification.service;

import com.dandimate.durooduroo.notification.domain.Notification;

/** DB 알림이 커밋된 뒤에만 브라우저 푸시를 보낸다. */
record PushNotificationEvent(Notification notification) {}
