package nbc.c1oud_mall.order.application;

import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.common.exception.ErrorCode;
import nbc.c1oud_mall.payment.domain.Payment;
import nbc.c1oud_mall.payment.domain.PaymentStatus;
import nbc.c1oud_mall.payment.infrastructure.PaymentJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class OrderCancelServiceTest {

    private static final Long ORDER_ID = 1L;
    private static final Long USER_ID = 10L;

    @Mock
    private PaymentJpaRepository paymentJpaRepository;

    @InjectMocks
    private OrderCancelService orderCancelService;

    @Test
    @DisplayName("결제대기 상태 Payment를 실패 처리한다")
    void cancelPendingPayment_success() {
        // given
        Payment payment = paymentWithStatus(PaymentStatus.PENDING);

        given(paymentJpaRepository.findByOrderId(ORDER_ID))
                .willReturn(Optional.of(payment));

        // when
        boolean result = orderCancelService.cancelPendingPayment(ORDER_ID);

        // then
        assertThat(result).isTrue();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("이미 실패 처리된 Payment는 false를 반환한다")
    void cancelPendingPayment_alreadyFailed_returnFalse() {
        // given
        Payment payment = paymentWithStatus(PaymentStatus.FAILED);

        given(paymentJpaRepository.findByOrderId(ORDER_ID))
                .willReturn(Optional.of(payment));

        // when
        boolean result = orderCancelService.cancelPendingPayment(ORDER_ID);

        // then
        assertThat(result).isFalse();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("COMPLETED 상태 Payment는 취소할 수 없다")
    void cancelPendingPayment_completed_throwsException() {
        // given
        Payment payment = paymentWithStatus(PaymentStatus.COMPLETED);

        given(paymentJpaRepository.findByOrderId(ORDER_ID))
                .willReturn(Optional.of(payment));

        // when & then
        assertThatThrownBy(() -> orderCancelService.cancelPendingPayment(ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_INVALID_STATUS);
    }

    @Test
    @DisplayName("Payment가 없으면 PAYMENT_NOT_FOUND 예외가 발생한다")
    void cancelPendingPayment_notFound_throwsException() {
        // given
        given(paymentJpaRepository.findByOrderId(ORDER_ID))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> orderCancelService.cancelPendingPayment(ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);
    }

    private Payment paymentWithStatus(PaymentStatus status) {
        return Payment.rehydrate(
                1L,
                "portone-payment-id",
                ORDER_ID,
                USER_ID,
                30_000L,
                25_000L,
                5_000L,
                status == PaymentStatus.COMPLETED ? 300L : 0L,
                status,
                null,
                status == PaymentStatus.COMPLETED ? "pg-tx-id" : null
        );
    }
}