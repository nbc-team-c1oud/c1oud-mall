package nbc.c1oud_mall.payment.application;

import nbc.c1oud_mall.payment.application.dto.PaymentInitiationResult;
import nbc.c1oud_mall.payment.application.dto.command.PaymentInitiationCommand;

public interface PaymentInitiationUseCase {

    PaymentInitiationResult initiate(PaymentInitiationCommand command);
}
