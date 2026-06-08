package nbc.c1oud_mall.payment.application;

import nbc.c1oud_mall.payment.application.dto.PaymentConfirmationResult;

public interface PaymentWebhookUseCase {

    PaymentConfirmationResult handleWebhook(String portonePaymentId);
}
