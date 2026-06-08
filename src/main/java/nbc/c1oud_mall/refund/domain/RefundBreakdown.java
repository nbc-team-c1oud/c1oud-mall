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

    /**
     * 환불 대상 결제로 적립됐던 포인트 중 비례 회수할 금액.
     * 환불 시 PointService.cancelEarnedPoints로 사용자 잔액에서 차감 (잔액 부족 시 잔액까지만).
     */
    @Column(name = "point_earned_recover_amount", nullable = false)
    private long pointEarnedRecoverAmount;

    public RefundBreakdown(long pgRefundAmount, long pointRefundAmount, long pointEarnedRecoverAmount) {
        if (pgRefundAmount < 0 || pointRefundAmount < 0 || pointEarnedRecoverAmount < 0) {
            throw new IllegalArgumentException(
                    "Refund amounts must be non-negative: pg=" + pgRefundAmount
                            + ", point=" + pointRefundAmount
                            + ", earnedRecover=" + pointEarnedRecoverAmount);
        }
        this.pgRefundAmount = pgRefundAmount;
        this.pointRefundAmount = pointRefundAmount;
        this.pointEarnedRecoverAmount = pointEarnedRecoverAmount;
    }

    public long getTotalRefundAmount() {
        return pgRefundAmount + pointRefundAmount;
    }
}
