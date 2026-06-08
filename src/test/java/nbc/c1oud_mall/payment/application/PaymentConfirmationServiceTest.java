package nbc.c1oud_mall.payment.application;

import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.common.exception.ErrorCode;
import nbc.c1oud_mall.order.application.OrderService;
import nbc.c1oud_mall.payment.application.dto.PaymentConfirmationResult;
import nbc.c1oud_mall.payment.application.dto.PortOnePaymentInfo;
import nbc.c1oud_mall.payment.application.dto.PortOnePaymentStatus;
import nbc.c1oud_mall.payment.application.dto.command.PaymentConfirmationCommand;
import nbc.c1oud_mall.cart.application.CartService;
import nbc.c1oud_mall.payment.domain.Payment;
import nbc.c1oud_mall.payment.domain.PaymentStatus;
import nbc.c1oud_mall.payment.infrastructure.PaymentJpaRepository;
import nbc.c1oud_mall.point.application.PointService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentConfirmationServiceTest {

    private static final String PORTONE_ID = "portone-payment-test-001";
    private static final Long ORDER_ID = 1L;
    private static final Long USER_ID = 100L;

    @Mock
    private PaymentJpaRepository paymentRepository;
    @Mock
    private PortOnePaymentQueryPort portOnePaymentQueryPort;
    @Mock
    private PaymentCompensationService paymentCompensationService;
    @Mock
    private OrderService orderService;
    @Mock
    private PointService pointService;
    @Mock
    private CartService cartService;

    @InjectMocks
    private PaymentConfirmationService service;

    private Payment pendingPayment(long pgAmount, long pointUsed) {
        return Payment.rehydrate(
                42L, PORTONE_ID, ORDER_ID, USER_ID,
                pgAmount + pointUsed, pgAmount, pointUsed,
                0L, PaymentStatus.PENDING, null, null);
    }

    private PortOnePaymentInfo paidInfo(long totalAmount) {
        return new PortOnePaymentInfo(
                PORTONE_ID, PortOnePaymentStatus.PAID, totalAmount, "TOSSPAYMENTS", "pg-tx-001");
    }

    @Test
    @DisplayName("정상 확정: COMPLETED 전이 + 협력 BC 호출 + 적립 포인트 1% 호출")
    void confirm_normal_flow() {
        Payment payment = pendingPayment(9_000L, 1_000L);
        when(portOnePaymentQueryPort.query(PORTONE_ID)).thenReturn(paidInfo(9_000L));
        when(paymentRepository.findByPortonePaymentId(PORTONE_ID)).thenReturn(Optional.of(payment));
        when(pointService.calculateEarnedAmount(10_000L)).thenReturn(100L);

        PaymentConfirmationResult result =
                service.confirm(new PaymentConfirmationCommand(PORTONE_ID, USER_ID, ORDER_ID));

        assertThat(result.paymentId()).isEqualTo(42L);
        assertThat(result.alreadyCompleted()).isFalse();
        assertThat(result.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);

        verify(orderService).completeOrder(ORDER_ID);
        verify(pointService).deductPoints(eq(USER_ID), eq(1_000L), eq(payment));
        verify(pointService).accruePoints(eq(USER_ID), eq(100L), eq(payment));
        verify(cartService).clearCart(USER_ID);
        verifyNoInteractions(paymentCompensationService);
    }

    @Test
    @DisplayName("멱등: 이미 COMPLETED → 부수효과 없이 alreadyCompleted=true, 보상 미호출")
    void confirm_idempotent_already_completed() {
        Payment completed = Payment.rehydrate(
                42L, PORTONE_ID, ORDER_ID, USER_ID,
                10_000L, 9_000L, 1_000L, 0L,
                PaymentStatus.COMPLETED, LocalDateTime.now(), "pg-existing");
        when(portOnePaymentQueryPort.query(PORTONE_ID)).thenReturn(paidInfo(9_000L));
        when(paymentRepository.findByPortonePaymentId(PORTONE_ID)).thenReturn(Optional.of(completed));

        PaymentConfirmationResult result =
                service.confirm(new PaymentConfirmationCommand(PORTONE_ID, USER_ID, ORDER_ID));

        assertThat(result.alreadyCompleted()).isTrue();
        verifyNoInteractions(orderService);
        verifyNoInteractions(paymentCompensationService);
    }

    @Test
    @DisplayName("소유권 위반(PM006) → 보상 미호출, 예외만 재 throw")
    void confirm_ownership_violation_skips_compensation() {
        Payment payment = pendingPayment(9_000L, 1_000L);
        when(portOnePaymentQueryPort.query(PORTONE_ID)).thenReturn(paidInfo(9_000L));
        when(paymentRepository.findByPortonePaymentId(PORTONE_ID)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() ->
                service.confirm(new PaymentConfirmationCommand(PORTONE_ID, 999L, ORDER_ID)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_AUTHORIZATION_FAILED);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verifyNoInteractions(paymentCompensationService);
        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("orderId 불일치(PM010) → 보상 미호출, 예외만 재 throw")
    void confirm_order_id_mismatch_skips_compensation() {
        Payment payment = pendingPayment(9_000L, 1_000L);
        when(portOnePaymentQueryPort.query(PORTONE_ID)).thenReturn(paidInfo(9_000L));
        when(paymentRepository.findByPortonePaymentId(PORTONE_ID)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() ->
                service.confirm(new PaymentConfirmationCommand(PORTONE_ID, USER_ID, 999L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_ORDER_MISMATCH);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verifyNoInteractions(paymentCompensationService);
        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("PortOne not PAID(PM007) → 보상 호출 + 예외 재 throw")
    void confirm_portone_not_paid_triggers_compensation() {
        Payment payment = pendingPayment(9_000L, 1_000L);
        PortOnePaymentInfo failedInfo = new PortOnePaymentInfo(
                PORTONE_ID, PortOnePaymentStatus.FAILED, 9_000L, "TOSSPAYMENTS", "pg-tx-001");
        when(portOnePaymentQueryPort.query(PORTONE_ID)).thenReturn(failedInfo);
        when(paymentRepository.findByPortonePaymentId(PORTONE_ID)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() ->
                service.confirm(new PaymentConfirmationCommand(PORTONE_ID, USER_ID, ORDER_ID)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PORTONE_PAYMENT_NOT_PAID);

        verify(paymentCompensationService).compensate(eq(PORTONE_ID), anyString());
        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("금액 불일치(PM001) → 보상 호출 + 예외 재 throw")
    void confirm_amount_mismatch_triggers_compensation() {
        Payment payment = pendingPayment(9_000L, 1_000L);
        when(portOnePaymentQueryPort.query(PORTONE_ID)).thenReturn(paidInfo(8_999L));
        when(paymentRepository.findByPortonePaymentId(PORTONE_ID)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() ->
                service.confirm(new PaymentConfirmationCommand(PORTONE_ID, USER_ID, ORDER_ID)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);

        verify(paymentCompensationService).compensate(eq(PORTONE_ID), anyString());
    }

    @Test
    @DisplayName("Payment 미존재 → PM008, 보상 미호출 (검증 진입 전이라)")
    void confirm_payment_not_found_skips_compensation() {
        when(portOnePaymentQueryPort.query(PORTONE_ID)).thenReturn(paidInfo(9_000L));
        when(paymentRepository.findByPortonePaymentId(PORTONE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.confirm(new PaymentConfirmationCommand(PORTONE_ID, USER_ID, ORDER_ID)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);

        verifyNoInteractions(paymentCompensationService);
    }

    @Test
    @DisplayName("포인트 사용 0 → deductPoints 미호출, 적립은 정상 호출")
    void confirm_no_point_used_skips_deduct() {
        Payment payment = pendingPayment(10_000L, 0L);
        when(portOnePaymentQueryPort.query(PORTONE_ID)).thenReturn(paidInfo(10_000L));
        when(paymentRepository.findByPortonePaymentId(PORTONE_ID)).thenReturn(Optional.of(payment));
        when(pointService.calculateEarnedAmount(10_000L)).thenReturn(100L);

        service.confirm(new PaymentConfirmationCommand(PORTONE_ID, USER_ID, ORDER_ID));

        verify(pointService, never()).deductPoints(any(), anyLong(), any());
        verify(pointService).accruePoints(eq(USER_ID), eq(100L), eq(payment));
        verify(orderService).completeOrder(ORDER_ID);
    }
}
