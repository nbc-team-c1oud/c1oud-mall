package nbc.c1oud_mall.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import nbc.c1oud_mall.common.domain.BaseEntity;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "webhook_events",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_webhook_events_payment_event",
                columnNames = {"portone_payment_id", "event_type"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WebhookEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "portone_payment_id", nullable = false, length = 36)
    private String portonePaymentId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "webhook_id", length = 100)
    private String webhookId;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "process_status", nullable = false, length = 16)
    private WebhookEventStatus processStatus;

    private WebhookEvent(String portonePaymentId, String eventType, String webhookId) {
        this.portonePaymentId = portonePaymentId;
        this.eventType = eventType;
        this.webhookId = webhookId;
        this.receivedAt = LocalDateTime.now();
        this.processStatus = WebhookEventStatus.RECEIVED;
    }

    public static WebhookEvent receive(String portonePaymentId, String eventType, String webhookId) {
        return new WebhookEvent(portonePaymentId, eventType, webhookId);
    }
}
