package io.github.manormachine2207.hrsuite.notification.dto;

import io.github.manormachine2207.hrsuite.notification.SmtpSecurity;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Body for PUT /api/v1/platform/smtp (ADR-019 Stufe 3). No password value (SDR-004). */
public record UpdateSmtpRelayRequest(
        @NotBlank String host,
        @Min(1) @Max(65535) int port,
        @NotNull SmtpSecurity security,
        @NotBlank @Email String fromAddress,
        String fromName,
        String username,
        String passwordRef,
        boolean enabled) {
}
