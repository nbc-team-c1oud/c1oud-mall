package nbc.c1oud_mall.payment.application;

import lombok.RequiredArgsConstructor;
import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.common.exception.ErrorCode;
import nbc.c1oud_mall.payment.application.dto.PaymentConfirmationResult;
import nbc.c1oud_mall.payment.application.dto.PortOnePaymentInfo;
import nbc.c1oud_mall.payment.application.dto.command.PaymentConfirmationCommand;
import nbc.c1oud_mall.payment.domain.Payment;
import nbc.c1oud_mall.cart.application.CartService;
import nbc.c1oud_mall.payment.infrastructure.PaymentJpaRepository;
import nbc.c1oud_mall.order.application.OrderService;
import nbc.c1oud_mall.payment.infrastructure.mock.MockInventoryService;
import nbc.c1oud_mall.point.application.PointService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class PaymentConfirmationService implements PaymentConfirmationUseCase, PaymentWebhookUseCase {

    private final PaymentJpaRepository paymentRepository;
    private final PortOnePaymentQueryPort portOnePaymentQueryPort;
    private final PaymentCompensationService paymentCompensationService;
    private final WebhookEventRegistrar webhookEventRegistrar;
    private final OrderService orderService;
    private final PointService pointService;
    private final CartService cartService;
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
            payment.verifyOrderId(command.orderId());
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

        orderService.completeOrder(payment.getOrderId());
        if (payment.getBreakdown().getPointUsedAmount() > 0L) {
            pointService.deductPoints(payment.getUserId(),
                    payment.getBreakdown().getPointUsedAmount(), payment);
        }
        if (pointEarnedAmount > 0L) {
            pointService.accruePoints(payment.getUserId(), pointEarnedAmount, payment);
        }
        cartService.clearCart(payment.getUserId());
        mockInventoryService.confirmByOrderId(payment.getOrderId());

        return PaymentConfirmationResult.confirmed(payment);
    }

    @Override
    public PaymentConfirmationResult handleWebhook(String portonePaymentId) {
        // INSERT-first: (portonePaymentId, eventType) UNIQUE로 동시 중복 웹훅 차단
        boolean registered = webhookEventRegistrar.tryRegister(portonePaymentId, "Transaction.Paid");
        if (!registered) {
            return paymentRepository.findByPortonePaymentId(portonePaymentId)
                    .map(PaymentConfirmationResult::alreadyCompleted)
                    .orElseThrow(() -> BusinessException.withDetail(
                            ErrorCode.PAYMENT_NOT_FOUND,
                            "portonePaymentId=" + portonePaymentId));
        }
        Payment payment = paymentRepository.findByPortonePaymentId(portonePaymentId)
                .orElseThrow(() -> BusinessException.withDetail(
                        ErrorCode.PAYMENT_NOT_FOUND,
                        "portonePaymentId=" + portonePaymentId));
        return confirm(new PaymentConfirmationCommand(
                portonePaymentId, payment.getUserId(), payment.getOrderId()));
    }

    private boolean isCompensable(BusinessException ex) {
        ErrorCode code = ex.getErrorCode();
        return code == ErrorCode.PAYMENT_AMOUNT_MISMATCH
                || code == ErrorCode.PORTONE_PAYMENT_NOT_PAID;
    }
}
