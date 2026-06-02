package nbc.c1oud_mall.payment.application;

import nbc.c1oud_mall.payment.application.dto.PaymentConfirmationResult;
import nbc.c1oud_mall.payment.application.dto.command.PaymentConfirmationCommand;

public interface PaymentConfirmationUseCase {

    PaymentConfirmationResult confirm(PaymentConfirmationCommand command);
}
