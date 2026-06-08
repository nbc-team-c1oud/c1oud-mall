package nbc.c1oud_mall.refund.domain;

/**
 * Refund Aggregate 생성에 필요한 결제 정보를 평탄화한 도메인 입력 record.
 *
 * refund.domain이 payment.domain.Payment를 직접 import하지 않도록
 * application 단에서 Payment 엔티티 값을 추출해 채워 넘긴다.
 */
public record RefundablePayment(
        Long paymentId,
        Long userId,
        boolean isCompleted,
        long totalAmount,
        long pgAmount,
        long pointUsedAmount,
        String portonePaymentId
) {
}
