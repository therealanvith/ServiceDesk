package com.servicedesk.api.dto;

/** Public sign-in options for the login page; a null client id means Google sign-in is off. */
public record AuthConfigResponse(String googleClientId, boolean registrationEnabled) {}
