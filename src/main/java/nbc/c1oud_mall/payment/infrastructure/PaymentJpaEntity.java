package nbc.c1oud_mall.payment.infrastructure;

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
import nbc.c1oud_mall.payment.domain.Payment;
import nbc.c1oud_mall.payment.domain.PaymentStatus;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "payments",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_payments_portone_payment_id",
                        columnNames = "portone_payment_id"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentJpaEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "portone_payment_id", nullable = false, updatable = false, length = 36)
    private String portonePaymentId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "total_amount", nullable = false)
    private long totalAmount;

    @Column(name = "pg_amount", nullable = false)
    private long pgAmount;

    @Column(name = "point_used_amount", nullable = false)
    private long pointUsedAmount;

    @Column(name = "point_earned_amount", nullable = false)
    private long pointEarnedAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PaymentStatus status;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "pg_tx_id", length = 100)
    private String pgTxId;

    private PaymentJpaEntity(Long id, String portonePaymentId, Long orderId, Long userId,
                             long totalAmount, long pgAmount, long pointUsedAmount,
                             long pointEarnedAmount, PaymentStatus status,
                             LocalDateTime confirmedAt, String pgTxId) {
        this.id = id;
        this.portonePaymentId = portonePaymentId;
        this.orderId = orderId;
        this.userId = userId;
        this.totalAmount = totalAmount;
        this.pgAmount = pgAmount;
        this.pointUsedAmount = pointUsedAmount;
        this.pointEarnedAmount = pointEarnedAmount;
        this.status = status;
        this.confirmedAt = confirmedAt;
        this.pgTxId = pgTxId;
    }

    public static PaymentJpaEntity from(Payment payment) {
        return new PaymentJpaEntity(
                payment.getId(),
                payment.getPortonePaymentId(),
                payment.getOrderId(),
                payment.getUserId(),
                payment.getBreakdown().totalAmount(),
                payment.getBreakdown().pgAmount(),
                payment.getBreakdown().pointUsedAmount(),
                payment.getPointEarnedAmount(),
                payment.getStatus(),
                payment.getConfirmedAt(),
                payment.getPgTxId()
        );
    }

    public Payment toDomain() {
        return Payment.rehydrate(
                id,
                portonePaymentId,
                orderId,
                userId,
                totalAmount,
                pgAmount,
                pointUsedAmount,
                pointEarnedAmount,
                status,
                confirmedAt,
                pgTxId
        );
    }
}
