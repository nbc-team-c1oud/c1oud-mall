package nbc.c1oud_mall.payment.domain;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    private Long id;
    private String portonePaymentId;
    private Long orderId;
    private Long userId;
    private PaymentBreakdown breakdown;
    private long pointEarnedAmount;
    private PaymentStatus status;
    private LocalDateTime confirmedAt;
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
