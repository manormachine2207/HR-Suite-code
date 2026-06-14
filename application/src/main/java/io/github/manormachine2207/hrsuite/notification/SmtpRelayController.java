package io.github.manormachine2207.hrsuite.notification;

import io.github.manormachine2207.hrsuite.notification.dto.SmtpRelayResponse;
import io.github.manormachine2207.hrsuite.notification.dto.TestSendRequest;
import io.github.manormachine2207.hrsuite.notification.dto.TestSendResponse;
import io.github.manormachine2207.hrsuite.notification.dto.UpdateSmtpRelayRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform SMTP relay API (ADR-019 Stufe 3). Global, platform-admin-guarded via
 * SecurityConfig ({@code /api/v1/platform/**}). No password value crosses this API
 * (SDR-004): GET returns the env-var reference + a {@code secretConfigured} flag.
 */
@RestController
@RequestMapping("/api/v1/platform/smtp")
public class SmtpRelayController {

    private final SmtpRelayService service;

    public SmtpRelayController(SmtpRelayService service) {
        this.service = service;
    }

    @GetMapping
    public SmtpRelayResponse get() {
        return SmtpRelayResponse.from(service.get(), service.secretConfigured());
    }

    @PutMapping
    public SmtpRelayResponse update(@Valid @RequestBody UpdateSmtpRelayRequest request) {
        SmtpRelayConfig updated = service.update(request);
        return SmtpRelayResponse.from(updated, service.secretConfigured());
    }

    @PostMapping("/test")
    public TestSendResponse test(@Valid @RequestBody TestSendRequest request) {
        return service.testSend(request.to());
    }
}
