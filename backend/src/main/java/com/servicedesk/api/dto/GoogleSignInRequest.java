package com.servicedesk.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The ID token ("credential") returned to the browser by Google Identity Services. */
public record GoogleSignInRequest(
    @NotBlank(message = "Google credential is required")
    @Size(max = 8192, message = "Google credential is too large")
    String credential) {}
