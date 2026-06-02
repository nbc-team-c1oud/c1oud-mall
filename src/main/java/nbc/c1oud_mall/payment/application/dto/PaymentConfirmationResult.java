package nbc.c1oud_mall.payment.application.dto;

import nbc.c1oud_mall.payment.domain.Payment;
import nbc.c1oud_mall.payment.domain.PaymentStatus;

public record PaymentConfirmationResult(
        Long paymentId,
        String portonePaymentId,
        PaymentStatus status,
        boolean alreadyCompleted
) {

    public static PaymentConfirmationResult confirmed(Payment payment) {
        return new PaymentConfirmationResult(
                payment.getId(),
                payment.getPortonePaymentId(),
                payment.getStatus(),
                false
        );
    }

    public static PaymentConfirmationResult alreadyCompleted(Payment payment) {
        return new PaymentConfirmationResult(
                payment.getId(),
                payment.getPortonePaymentId(),
                payment.getStatus(),
                true
        );
    }
}
