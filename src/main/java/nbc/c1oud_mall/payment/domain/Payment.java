package nbc.c1oud_mall.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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
import java.util.UUID;

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
public class Payment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "portone_payment_id", nullable = false, updatable = false, length = 36)
    private String portonePaymentId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Embedded
    private PaymentBreakdown breakdown;

    @Column(name = "point_earned_amount", nullable = false)
    private long pointEarnedAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PaymentStatus status;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "pg_tx_id", length = 100)
    private String pgTxId;

    private Payment(Long id, String portonePaymentId, Long orderId, Long userId,
                    PaymentBreakdown breakdown, long pointEarnedAmount,
                    PaymentStatus status, LocalDateTime confirmedAt, String pgTxId) {
        this.id = id;
        this.portonePaymentId = portonePaymentId;
        this.orderId = orderId;
        this.userId = userId;
        this.breakdown = breakdown;
        this.pointEarnedAmount = pointEarnedAmount;
        this.status = status;
        this.confirmedAt = confirmedAt;
        this.pgTxId = pgTxId;
    }

    public static Payment of(Long orderId, Long userId,
                             long totalAmount, long pgAmount, long pointUsedAmount) {
        PaymentBreakdown breakdown = new PaymentBreakdown(totalAmount, pgAmount, pointUsedAmount);
        return new Payment(
                null,
                UUID.randomUUID().toString(),
                orderId,
                userId,
                breakdown,
                0L,
                PaymentStatus.PENDING,
                null,
                null
        );
    }

    public static Payment rehydrate(Long id, String portonePaymentId, Long orderId, Long userId,
                                    long totalAmount, long pgAmount, long pointUsedAmount,
                                    long pointEarnedAmount, PaymentStatus status,
                                    LocalDateTime confirmedAt, String pgTxId) {
        return new Payment(
                id,
                portonePaymentId,
                orderId,
                userId,
                new PaymentBreakdown(totalAmount, pgAmount, pointUsedAmount),
                pointEarnedAmount,
                status,
                confirmedAt,
                pgTxId
        );
    }
}
