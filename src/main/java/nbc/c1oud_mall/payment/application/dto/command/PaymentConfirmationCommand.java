package nbc.c1oud_mall.payment.application.dto.command;

public record PaymentConfirmationCommand(
        String portonePaymentId,
        Long requestUserId,
        Long orderId
) {
}
