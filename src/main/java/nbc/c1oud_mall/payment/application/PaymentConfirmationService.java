package nbc.c1oud_mall.payment.application;

import lombok.RequiredArgsConstructor;
import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.common.exception.ErrorCode;
import nbc.c1oud_mall.payment.application.dto.PaymentConfirmationResult;
import nbc.c1oud_mall.payment.application.dto.PortOnePaymentInfo;
import nbc.c1oud_mall.payment.application.dto.command.PaymentConfirmationCommand;
import nbc.c1oud_mall.payment.domain.Payment;
import nbc.c1oud_mall.payment.infrastructure.PaymentJpaRepository;
import nbc.c1oud_mall.payment.infrastructure.mock.MockCartService;
import nbc.c1oud_mall.payment.infrastructure.mock.MockInventoryService;
import nbc.c1oud_mall.payment.infrastructure.mock.MockOrderService;
import nbc.c1oud_mall.payment.infrastructure.mock.MockPointService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class PaymentConfirmationService implements PaymentConfirmationUseCase {

    private final PaymentJpaRepository paymentRepository;
    private final PortOnePaymentQueryPort portOnePaymentQueryPort;
    private final PaymentCompensationService paymentCompensationService;
    private final MockOrderService mockOrderService;
    private final MockPointService mockPointService;
    private final MockCartService mockCartService;
    private final MockInventoryService mockInventoryService;

    @Override
    public PaymentConfirmationResult confirm(PaymentConfirmationCommand command) {
        PortOnePaymentInfo info = portOnePaymentQueryPort.query(command.portonePaymentId());

        Payment payment = paymentRepository.findByPortonePaymentId(command.portonePaymentId())
                .orElseThrow(() -> BusinessException.withDetail(
                        ErrorCode.PAYMENT_NOT_FOUND,
                        "portonePaymentId=" + command.portonePaymentId()
                ));

        if (payment.isCompleted()) {
            return PaymentConfirmationResult.alreadyCompleted(payment);
        }

        try {
            payment.verifyOwnership(command.requestUserId());
            payment.verifyPortOneStatus(info.status());
            payment.verifyAmount(info.totalAmount());
        } catch (BusinessException ex) {
            if (isCompensable(ex)) {
                paymentCompensationService.compensate(
                        command.portonePaymentId(), ex.getMessage());
            }
            throw ex;
        }

        long pointEarnedAmount = 0L;
        payment.markCompleted(info.pgTxId(), pointEarnedAmount, LocalDateTime.now());

        mockOrderService.completeOrder(payment.getOrderId());
        if (payment.getBreakdown().getPointUsedAmount() > 0L) {
            mockPointService.deductPoints(payment.getUserId(),
                    payment.getBreakdown().getPointUsedAmount());
        }
        if (pointEarnedAmount > 0L) {
            mockPointService.accruePoints(payment.getUserId(), pointEarnedAmount);
        }
        mockCartService.clearByUserId(payment.getUserId());
        mockInventoryService.confirmByOrderId(payment.getOrderId());

        return PaymentConfirmationResult.confirmed(payment);
    }

    private boolean isCompensable(BusinessException ex) {
        ErrorCode code = ex.getErrorCode();
        return code == ErrorCode.PAYMENT_AMOUNT_MISMATCH
                || code == ErrorCode.PORTONE_PAYMENT_NOT_PAID;
    }
}
