package com.servicedesk.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** An account's email address; blank removes it (no email notifications). */
public record UpdateEmailRequest(
    @Email(message = "Enter a valid email address")
        @Size(max = 100, message = "Email must not exceed 100 characters")
        String email) {}
