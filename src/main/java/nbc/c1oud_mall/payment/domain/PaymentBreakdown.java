package nbc.c1oud_mall.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.common.exception.ErrorCode;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentBreakdown {

    @Column(name = "total_amount", nullable = false)
    private long totalAmount;

    @Column(name = "pg_amount", nullable = false)
    private long pgAmount;

    @Column(name = "point_used_amount", nullable = false)
    private long pointUsedAmount;

    public PaymentBreakdown(long totalAmount, long pgAmount, long pointUsedAmount) {
        if (totalAmount < 0 || pgAmount < 0 || pointUsedAmount < 0) {
            throw new BusinessException(ErrorCode.PAYMENT_INVALID_AMOUNT);
        }
        if (totalAmount != pgAmount + pointUsedAmount) {
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }
        this.totalAmount = totalAmount;
        this.pgAmount = pgAmount;
        this.pointUsedAmount = pointUsedAmount;
    }
}
