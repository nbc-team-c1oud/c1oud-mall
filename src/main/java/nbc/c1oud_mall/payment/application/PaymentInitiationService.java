package nbc.c1oud_mall.payment.application;

import lombok.RequiredArgsConstructor;
import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.common.exception.ErrorCode;
import nbc.c1oud_mall.payment.application.dto.PaymentInitiationResult;
import nbc.c1oud_mall.payment.application.dto.command.PaymentInitiationCommand;
import nbc.c1oud_mall.payment.domain.Payment;
import nbc.c1oud_mall.payment.infrastructure.PaymentJpaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PaymentInitiationService implements PaymentInitiationUseCase {

    private final PaymentJpaRepository paymentRepository;

    @Override
    public PaymentInitiationResult initiate(PaymentInitiationCommand command) {
        Payment payment = Payment.of(
                command.orderId(),
                command.userId(),
                command.totalAmount(),
                command.pgAmount(),
                command.pointUsedAmount()
        );

        try {
            Payment saved = paymentRepository.saveAndFlush(payment);
            return PaymentInitiationResult.from(saved);
        } catch (DataIntegrityViolationException ex) {
            throw BusinessException.withDetail(
                    ErrorCode.PAYMENT_DUPLICATE_PAYMENT_ID,
                    "portonePaymentId=" + payment.getPortonePaymentId()
            );
        }
    }
}
