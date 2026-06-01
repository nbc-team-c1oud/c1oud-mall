package nbc.c1oud_mall.payment.application.dto.command;

public record PaymentInitiationCommand(
        Long orderId,
        Long userId,
        long totalAmount,
        long pgAmount,
        long pointUsedAmount
) {
}
