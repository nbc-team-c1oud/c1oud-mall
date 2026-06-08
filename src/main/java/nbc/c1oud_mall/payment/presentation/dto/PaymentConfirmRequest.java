package nbc.c1oud_mall.payment.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import nbc.c1oud_mall.payment.application.dto.command.PaymentConfirmationCommand;

public record PaymentConfirmRequest(
        @NotNull Long orderId,
        @NotBlank String portonePaymentId
) {
    public PaymentConfirmationCommand toCommand(Long userId) {
        return new PaymentConfirmationCommand(portonePaymentId, userId, orderId);
    }
}
