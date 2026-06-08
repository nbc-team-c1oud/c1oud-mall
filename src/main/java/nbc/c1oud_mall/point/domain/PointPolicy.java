package nbc.c1oud_mall.point.domain;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 포인트 적립 정책. ADR-0009 참조.
 *
 * <p>적립액 = floor(totalAmount × accrualRateBasisPoints / 10_000)
 * 예: basis points 100 = 1.00% — 10,000원 결제 시 100p 적립.
 *
 * <p>basis points 단위로 외부화하여 0.01% 단위 미세 조정 시에도 정수 산술로 정확.
 */
@ConfigurationProperties(prefix = "points")
public record PointPolicy(int accrualRateBasisPoints) {

    public PointPolicy {
        if (accrualRateBasisPoints < 0) {
            throw new IllegalArgumentException(
                    "accrualRateBasisPoints must be non-negative: " + accrualRateBasisPoints);
        }
    }

    public long calculateEarnedAmount(long totalAmount) {
        if (totalAmount <= 0L) return 0L;
        return totalAmount * accrualRateBasisPoints / 10_000L;
    }

    /**
     * 환불에 따른 적립 포인트 비례 회수액 산정.
     * recoverAmount = floor(totalRefundAmount × earnedAmount / totalPaymentAmount)
     */
    public long calculateRecoverAmount(long totalRefundAmount, long earnedAmount, long totalPaymentAmount) {
        if (earnedAmount <= 0L || totalRefundAmount <= 0L || totalPaymentAmount <= 0L) return 0L;
        return totalRefundAmount * earnedAmount / totalPaymentAmount;
    }
}
