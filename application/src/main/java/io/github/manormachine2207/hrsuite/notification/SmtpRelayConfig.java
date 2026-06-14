package io.github.manormachine2207.hrsuite.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Global SMTP relay configuration (ADR-019 Stufe 3). System-scope singleton (id=1,
 * no RLS) — one platform-wide relay, platform-admin-guarded at the API. The SMTP
 * password is NEVER stored here: {@code passwordRef} names an environment variable
 * resolved at send time (SDR-004).
 */
@Entity
@Table(name = "smtp_relay_config")
public class SmtpRelayConfig {

    public static final short SINGLETON_ID = 1;

    @Id
    @Column(name = "id", nullable = false)
    private Short id = SINGLETON_ID;

    @Column(name = "host", nullable = false, length = 255)
    private String host;

    @Column(name = "port", nullable = false)
    private int port;

    @Enumerated(EnumType.STRING)
    @Column(name = "security", nullable = false, length = 16)
    private SmtpSecurity security;

    @Column(name = "from_address", nullable = false, length = 320)
    private String fromAddress;

    @Column(name = "from_name", length = 255)
    private String fromName;

    @Column(name = "username", length = 255)
    private String username;

    /** Name of the env var holding the SMTP password (never the password itself, SDR-004). */
    @Column(name = "password_ref", length = 128)
    private String passwordRef;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "verified", nullable = false)
    private boolean verified;

    @Column(name = "last_test_at")
    private OffsetDateTime lastTestAt;

    @Column(name = "last_test_outcome", length = 512)
    private String lastTestOutcome;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected SmtpRelayConfig() {
        // for JPA
    }

    /**
     * Applies an admin edit. Any config change resets {@code verified} to false —
     * a changed relay must be re-tested before it counts as verified.
     */
    public void update(String host, int port, SmtpSecurity security, String fromAddress,
                       String fromName, String username, String passwordRef, boolean enabled) {
        this.host = host;
        this.port = port;
        this.security = security;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
        this.username = username;
        this.passwordRef = passwordRef;
        this.enabled = enabled;
        this.verified = false;
        this.lastTestOutcome = null;
        this.lastTestAt = null;
    }

    /** Records a successful test send. */
    public void markTested(boolean success, String outcome, OffsetDateTime at) {
        this.verified = success;
        this.lastTestOutcome = outcome;
        this.lastTestAt = at;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public Short getId() { return id; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public SmtpSecurity getSecurity() { return security; }
    public String getFromAddress() { return fromAddress; }
    public String getFromName() { return fromName; }
    public String getUsername() { return username; }
    public String getPasswordRef() { return passwordRef; }
    public boolean isEnabled() { return enabled; }
    public boolean isVerified() { return verified; }
    public OffsetDateTime getLastTestAt() { return lastTestAt; }
    public String getLastTestOutcome() { return lastTestOutcome; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
