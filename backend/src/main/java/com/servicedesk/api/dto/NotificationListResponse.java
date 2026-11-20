package com.servicedesk.api.dto;

import java.util.List;

public record NotificationListResponse(long unreadCount, List<NotificationResponse> items) {}
