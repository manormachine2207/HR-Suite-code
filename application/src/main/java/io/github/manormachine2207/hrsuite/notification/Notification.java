package io.github.manormachine2207.hrsuite.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * In-app notification for one recipient (ADR-017 Stufe 2). Tenant-scoped (RLS,
 * ADR-008). {@code params} is a small bag the FE renders via i18n (BDR-005);
 * {@code antragId} is the deep-link target. {@code readAt == null} means unread.
 */
@Entity
@Table(name = "notification")
public class Notification {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "recipient_subject", nullable = false, updatable = false)
    private String recipientSubject;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 64, updatable = false)
    private NotificationType type;

    @Column(name = "antrag_id")
    private UUID antragId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "params", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> params;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "read_at")
    private OffsetDateTime readAt;

    protected Notification() {
        // for JPA
    }

    public Notification(UUID id, UUID tenantId, String recipientSubject, NotificationType type,
                        UUID antragId, Map<String, Object> params) {
        this.id = id;
        this.tenantId = tenantId;
        this.recipientSubject = recipientSubject;
        this.type = type;
        this.antragId = antragId;
        this.params = params == null ? Map.of() : params;
        this.createdAt = OffsetDateTime.now();
    }

    /** Marks the notification read (idempotent; keeps the first read timestamp). */
    public void markRead() {
        if (this.readAt == null) {
            this.readAt = OffsetDateTime.now();
        }
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getRecipientSubject() { return recipientSubject; }
    public NotificationType getType() { return type; }
    public UUID getAntragId() { return antragId; }
    public Map<String, Object> getParams() { return params == null ? Map.of() : params; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getReadAt() { return readAt; }
    public boolean isRead() { return readAt != null; }
}
