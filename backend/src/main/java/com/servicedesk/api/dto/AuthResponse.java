package com.servicedesk.api.dto;

public record AuthResponse(String token, UserProfileResponse user) {}
