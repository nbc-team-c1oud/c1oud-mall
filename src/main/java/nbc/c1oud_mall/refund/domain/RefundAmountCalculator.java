package nbc.c1oud_mall.refund.domain;

import java.util.List;

/**
 * 환불 금액 산정 도메인 서비스.
 *
 * - 총 환불 금액 = Σ (priceSnapshotAtPayment × quantity)
 * - 복합결제 비율 분리:
 *     pgRefundAmount    = floor(total × pgAmount / totalAmount)
 *     pointRefundAmount = total - pgRefundAmount   (잔액 흡수)
 * - 소수점 처리 정책: PG는 floor, 포인트가 끝수 흡수 (사용자 무손해 + 합계 보장)
 *
 * 정책 근거는 ADR-0008 참조.
 *
 * 순수 함수형 도메인 서비스 — 외부 의존 없음, 상태 없음.
 */
public class RefundAmountCalculator {

    public RefundBreakdown calculate(RefundablePayment payment, List<RefundItemRequest> items) {
        if (payment == null) {
            throw new IllegalArgumentException("RefundablePayment must not be null");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("RefundItems must not be empty");
        }
        if (payment.totalAmount() <= 0L) {
            throw new IllegalArgumentException(
                    "Payment totalAmount must be positive: " + payment.totalAmount());
        }

        long totalRefundAmount = 0L;
        for (RefundItemRequest item : items) {
            totalRefundAmount += item.priceSnapshotAtPayment() * item.quantity();
        }

        long pgRefundAmount;
        long pointRefundAmount;
        if (payment.pgAmount() == 0L) {
            // 포인트 전액 결제 — 환불 전액 포인트로
            pgRefundAmount = 0L;
            pointRefundAmount = totalRefundAmount;
        } else if (payment.pointUsedAmount() == 0L) {
            // PG 전액 결제 — 환불 전액 PG로
            pgRefundAmount = totalRefundAmount;
            pointRefundAmount = 0L;
        } else {
            // 복합결제 — PG floor + 포인트 잔액 흡수
            pgRefundAmount = (totalRefundAmount * payment.pgAmount()) / payment.totalAmount();
            pointRefundAmount = totalRefundAmount - pgRefundAmount;
        }

        return new RefundBreakdown(pgRefundAmount, pointRefundAmount);
    }
}
