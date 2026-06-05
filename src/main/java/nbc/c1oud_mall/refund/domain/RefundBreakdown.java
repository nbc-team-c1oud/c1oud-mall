package nbc.c1oud_mall.refund.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefundBreakdown {

    @Column(name = "pg_refund_amount", nullable = false)
    private long pgRefundAmount;

    @Column(name = "point_refund_amount", nullable = false)
    private long pointRefundAmount;

    public RefundBreakdown(long pgRefundAmount, long pointRefundAmount) {
        if (pgRefundAmount < 0 || pointRefundAmount < 0) {
            throw new IllegalArgumentException(
                    "Refund amounts must be non-negative: pg=" + pgRefundAmount
                            + ", point=" + pointRefundAmount);
        }
        this.pgRefundAmount = pgRefundAmount;
        this.pointRefundAmount = pointRefundAmount;
    }

    public long getTotalRefundAmount() {
        return pgRefundAmount + pointRefundAmount;
    }
}
