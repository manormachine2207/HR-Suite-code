package io.github.manormachine2207.hrsuite.notification.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Body for POST /api/v1/platform/smtp/test (ADR-019 Stufe 3). */
public record TestSendRequest(@NotBlank @Email String to) {
}
