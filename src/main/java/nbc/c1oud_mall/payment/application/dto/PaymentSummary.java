package nbc.c1oud_mall.payment.application.dto;

import nbc.c1oud_mall.payment.domain.Payment;
import nbc.c1oud_mall.payment.domain.PaymentStatus;

public record PaymentSummary(
        Long paymentId,
        PaymentStatus paymentStatus,
        long pgAmount,
        long pointUsedAmount,
        long pointEarnedAmount
) {
    public static PaymentSummary from(Payment payment) {
        return new PaymentSummary(
                payment.getId(),
                payment.getStatus(),
                payment.getBreakdown().getPgAmount(),
                payment.getBreakdown().getPointUsedAmount(),
                payment.getPointEarnedAmount()
        );
    }
}
