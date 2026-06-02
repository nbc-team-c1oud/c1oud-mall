package nbc.c1oud_mall.payment.application;

import lombok.RequiredArgsConstructor;
import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.common.exception.ErrorCode;
import nbc.c1oud_mall.payment.domain.Payment;
import nbc.c1oud_mall.payment.infrastructure.PaymentJpaRepository;
import nbc.c1oud_mall.payment.infrastructure.mock.MockInventoryService;
import nbc.c1oud_mall.payment.infrastructure.mock.MockOrderService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PaymentCompensationTxOp {

    private final PaymentJpaRepository paymentRepository;
    private final MockOrderService mockOrderService;
    private final MockInventoryService mockInventoryService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void compensateDb(String portonePaymentId, String reason) {
        Payment payment = paymentRepository.findByPortonePaymentId(portonePaymentId)
                .orElseThrow(() -> BusinessException.withDetail(
                        ErrorCode.PAYMENT_NOT_FOUND,
                        "portonePaymentId=" + portonePaymentId
                ));
        payment.markFailed(reason);
        mockOrderService.cancelOrder(payment.getOrderId());
        mockInventoryService.restoreByOrderId(payment.getOrderId());
    }
}
