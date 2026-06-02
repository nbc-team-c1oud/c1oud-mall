package nbc.c1oud_mall.payment.presentation.dto;

import nbc.c1oud_mall.payment.application.dto.PaymentConfirmationResult;
import nbc.c1oud_mall.payment.domain.PaymentStatus;

public record PaymentConfirmResponse(
        Long paymentId,
        String portonePaymentId,
        PaymentStatus status,
        boolean alreadyCompleted
) {
    public static PaymentConfirmResponse from(PaymentConfirmationResult result) {
        return new PaymentConfirmResponse(
                result.paymentId(),
                result.portonePaymentId(),
                result.status(),
                result.alreadyCompleted()
        );
    }
}
