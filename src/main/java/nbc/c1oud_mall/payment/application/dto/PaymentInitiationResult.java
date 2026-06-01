package nbc.c1oud_mall.payment.application.dto;

import nbc.c1oud_mall.payment.domain.Payment;

public record PaymentInitiationResult(
        Long paymentId,
        String portonePaymentId
) {

    public static PaymentInitiationResult from(Payment payment) {
        return new PaymentInitiationResult(payment.getId(), payment.getPortonePaymentId());
    }
}
