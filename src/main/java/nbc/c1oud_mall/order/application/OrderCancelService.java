package nbc.c1oud_mall.order.application;

import lombok.RequiredArgsConstructor;
import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.common.exception.ErrorCode;
import nbc.c1oud_mall.payment.domain.Payment;
import nbc.c1oud_mall.payment.domain.PaymentStatus;
import nbc.c1oud_mall.payment.infrastructure.PaymentJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderCancelService {

    private final PaymentJpaRepository paymentJpaRepository;

    @Transactional
    public boolean cancelPendingPayment(Long orderId) {
        Payment payment = paymentJpaRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        if (payment.getStatus() == PaymentStatus.FAILED) {
            return false;
        }

        if (payment.getStatus() != PaymentStatus.PENDING){
            throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATUS);
        }

        payment.markFailed("주문 취소로 인한 결제 대기 취소");
        return true;
    }


}
