package nbc.c1oud_mall.refund.application;

import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.common.exception.ErrorCode;
import nbc.c1oud_mall.order.domain.Order;
import nbc.c1oud_mall.order.domain.OrderItem;
import nbc.c1oud_mall.order.infrastructure.OrderJpaRepository;
import nbc.c1oud_mall.payment.application.PortOnePaymentCancelPort;
import nbc.c1oud_mall.payment.domain.Payment;
import nbc.c1oud_mall.payment.infrastructure.PaymentJpaRepository;
import nbc.c1oud_mall.refund.application.dto.RefundResult;
import nbc.c1oud_mall.refund.application.dto.command.RefundCommand;
import nbc.c1oud_mall.refund.application.dto.command.RefundItemCommand;
import nbc.c1oud_mall.refund.domain.Refund;
import nbc.c1oud_mall.refund.domain.RefundAmountCalculator;
import nbc.c1oud_mall.refund.domain.RefundBreakdown;
import nbc.c1oud_mall.refund.domain.RefundStatus;
import nbc.c1oud_mall.refund.infrastructure.RefundJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefundProcessServiceTest {

    private static final Long ORDER_ID = 10L;
    private static final Long USER_ID = 100L;
    private static final Long ORDER_ITEM_ID = 1L;
    private static final String PORTONE_ID = "portone-refund-test-001";
    private static final String REASON = "단순 변심";

    @Mock
    private PaymentJpaRepository paymentJpaRepository;
    @Mock
    private OrderJpaRepository orderJpaRepository;
    @Mock
    private RefundJpaRepository refundJpaRepository;
    @Mock
    private RefundAmountCalculator refundAmountCalculator;
    @Mock
    private RefundTxOp refundTxOp;
    @Mock
    private PortOnePaymentCancelPort portOnePaymentCancelPort;

    @InjectMocks
    private RefundProcessService service;

    @Test
    @DisplayName("정상 처리 (PG 취소 성공): RefundResult(PG_CANCELLED) 반환, cancel 1회 호출")
    void process_pg_cancel_success() {
        Payment payment = completedPayment(9_000L, 1_000L);
        Order order = mockOrderWithItem(ORDER_ITEM_ID, 5_000L, 2);
        RefundBreakdown breakdown = new RefundBreakdown(4_500L, 500L, 0L);
        Refund refund = mockRefund(42L);

        given(paymentJpaRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(payment));
        given(orderJpaRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(order));
        given(refundJpaRepository.sumRefundedQuantity(anyLong(), eq(ORDER_ITEM_ID))).willReturn(0L);
        given(refundAmountCalculator.calculate(any(), any())).willReturn(breakdown);
        given(refundTxOp.executeRefund(any(), any())).willReturn(refund);

        RefundCommand command = new RefundCommand(ORDER_ID, USER_ID,
                List.of(new RefundItemCommand(ORDER_ITEM_ID, 2)), REASON);

        RefundResult result = service.process(command);

        assertThat(result.finalStatus()).isEqualTo(RefundStatus.PG_CANCELLED);
        assertThat(result.refundId()).isEqualTo(42L);
        verify(portOnePaymentCancelPort).cancel(eq(PORTONE_ID), eq(4_500L), eq(REASON),
                eq("refund-42"));
        verify(refundTxOp).markPgCancelled(eq(42L), eq(null));
    }

    @Test
    @DisplayName("RF003 소유권 실패: BusinessException(RF003), txOp/cancel 미호출")
    void process_throws_rf003_when_ownership_mismatch() {
        Payment payment = completedPayment(9_000L, 1_000L);
        given(paymentJpaRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(payment));

        RefundCommand command = new RefundCommand(ORDER_ID, 999L,
                List.of(new RefundItemCommand(ORDER_ITEM_ID, 1)), REASON);

        assertThatThrownBy(() -> service.process(command))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.REFUND_OWNERSHIP_FAILED);

        verify(refundTxOp, never()).executeRefund(any(), any());
        verify(portOnePaymentCancelPort, never()).cancel(any(), any(), any(), any());
    }

    @Test
    @DisplayName("RF002 결제 미완료: BusinessException(RF002), txOp/cancel 미호출")
    void process_throws_rf002_when_payment_not_completed() {
        Payment payment = pendingPayment();
        given(paymentJpaRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(payment));

        RefundCommand command = new RefundCommand(ORDER_ID, USER_ID,
                List.of(new RefundItemCommand(ORDER_ITEM_ID, 1)), REASON);

        assertThatThrownBy(() -> service.process(command))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.REFUND_NOT_REFUNDABLE_STATE);

        verify(refundTxOp, never()).executeRefund(any(), any());
    }

    @Test
    @DisplayName("RF001 선검증 수량 초과: BusinessException(RF001), txOp 미호출")
    void process_throws_rf001_when_quantity_exceeded_in_prevalidation() {
        Payment payment = completedPayment(9_000L, 1_000L);
        Order order = mockOrderWithItem(ORDER_ITEM_ID, 5_000L, 2);

        given(paymentJpaRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(payment));
        given(orderJpaRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(order));
        given(refundJpaRepository.sumRefundedQuantity(anyLong(), eq(ORDER_ITEM_ID))).willReturn(2L); // 전량 소진

        RefundCommand command = new RefundCommand(ORDER_ID, USER_ID,
                List.of(new RefundItemCommand(ORDER_ITEM_ID, 1)), REASON);

        assertThatThrownBy(() -> service.process(command))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.REFUND_QUANTITY_EXCEEDED);

        verify(refundTxOp, never()).executeRefund(any(), any());
    }

    @Test
    @DisplayName("DB TX 실패: PG cancel 미호출, 예외 전파")
    void process_does_not_call_pg_cancel_when_tx_fails() {
        Payment payment = completedPayment(9_000L, 1_000L);
        Order order = mockOrderWithItem(ORDER_ITEM_ID, 5_000L, 2);
        RefundBreakdown breakdown = new RefundBreakdown(4_500L, 500L, 0L);

        given(paymentJpaRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(payment));
        given(orderJpaRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(order));
        given(refundJpaRepository.sumRefundedQuantity(anyLong(), eq(ORDER_ITEM_ID))).willReturn(0L);
        given(refundAmountCalculator.calculate(any(), any())).willReturn(breakdown);
        given(refundTxOp.executeRefund(any(), any()))
                .willThrow(new BusinessException(ErrorCode.REFUND_QUANTITY_EXCEEDED));

        RefundCommand command = new RefundCommand(ORDER_ID, USER_ID,
                List.of(new RefundItemCommand(ORDER_ITEM_ID, 2)), REASON);

        assertThatThrownBy(() -> service.process(command))
                .isInstanceOf(BusinessException.class);

        verify(portOnePaymentCancelPort, never()).cancel(any(), any(), any(), any());
    }

    @Test
    @DisplayName("PG 취소 실패: RefundResult(DB_COMMITTED) 반환, 예외 던지지 않음")
    void process_returns_db_committed_when_pg_cancel_fails() {
        Payment payment = completedPayment(9_000L, 1_000L);
        Order order = mockOrderWithItem(ORDER_ITEM_ID, 5_000L, 2);
        RefundBreakdown breakdown = new RefundBreakdown(4_500L, 500L, 0L);
        Refund refund = mockRefund(42L);

        given(paymentJpaRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(payment));
        given(orderJpaRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(order));
        given(refundJpaRepository.sumRefundedQuantity(anyLong(), eq(ORDER_ITEM_ID))).willReturn(0L);
        given(refundAmountCalculator.calculate(any(), any())).willReturn(breakdown);
        given(refundTxOp.executeRefund(any(), any())).willReturn(refund);
        willThrow(new BusinessException(ErrorCode.PORTONE_CANCEL_FAILED))
                .given(portOnePaymentCancelPort).cancel(any(), any(), any(), any());

        RefundCommand command = new RefundCommand(ORDER_ID, USER_ID,
                List.of(new RefundItemCommand(ORDER_ITEM_ID, 2)), REASON);

        RefundResult result = service.process(command);

        assertThat(result.finalStatus()).isEqualTo(RefundStatus.DB_COMMITTED);
        verify(refundTxOp, never()).markPgCancelled(anyLong(), anyString());
    }

    @Test
    @DisplayName("포인트 전액 결제(pgRefundAmount==0): cancel 미호출, PG_CANCELLED 반환")
    void process_skips_portone_when_pg_amount_is_zero() {
        Payment payment = pointOnlyPayment(5_000L);
        Order order = mockOrderWithItem(ORDER_ITEM_ID, 5_000L, 1);
        RefundBreakdown breakdown = new RefundBreakdown(0L, 5_000L, 0L);
        Refund refund = mockRefund(55L);

        given(paymentJpaRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(payment));
        given(orderJpaRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(order));
        given(refundJpaRepository.sumRefundedQuantity(anyLong(), eq(ORDER_ITEM_ID))).willReturn(0L);
        given(refundAmountCalculator.calculate(any(), any())).willReturn(breakdown);
        given(refundTxOp.executeRefund(any(), any())).willReturn(refund);

        RefundCommand command = new RefundCommand(ORDER_ID, USER_ID,
                List.of(new RefundItemCommand(ORDER_ITEM_ID, 1)), REASON);

        RefundResult result = service.process(command);

        assertThat(result.finalStatus()).isEqualTo(RefundStatus.PG_CANCELLED);
        verify(portOnePaymentCancelPort, never()).cancel(any(), any(), any(), any());
        verify(refundTxOp).markPgCancelled(eq(55L), eq(null));
    }

    // ── 헬퍼 ──

    private Payment completedPayment(long pgAmount, long pointUsedAmount) {
        Payment p = Payment.of(ORDER_ID, USER_ID, pgAmount + pointUsedAmount, pgAmount, pointUsedAmount);
        p.markCompleted(null, 0L, java.time.LocalDateTime.now());
        // portonePaymentId는 UUID.randomUUID()로 채번 → mock으로 덮기 어려우므로 stub 활용
        Payment mocked = mock(Payment.class);
        given(mocked.getId()).willReturn(1L);
        given(mocked.getUserId()).willReturn(USER_ID);
        given(mocked.isCompleted()).willReturn(true);
        given(mocked.getPortonePaymentId()).willReturn(PORTONE_ID);
        given(mocked.getBreakdown()).willReturn(p.getBreakdown());
        return mocked;
    }

    private Payment pendingPayment() {
        Payment mocked = mock(Payment.class);
        given(mocked.getUserId()).willReturn(USER_ID);
        given(mocked.isCompleted()).willReturn(false);
        return mocked;
    }

    private Payment pointOnlyPayment(long totalAmount) {
        Payment mocked = mock(Payment.class);
        given(mocked.getId()).willReturn(2L);
        given(mocked.getUserId()).willReturn(USER_ID);
        given(mocked.isCompleted()).willReturn(true);
        given(mocked.getPortonePaymentId()).willReturn(PORTONE_ID);
        nbc.c1oud_mall.payment.domain.PaymentBreakdown bd =
                new nbc.c1oud_mall.payment.domain.PaymentBreakdown(totalAmount, 0L, totalAmount);
        given(mocked.getBreakdown()).willReturn(bd);
        return mocked;
    }

    private Order mockOrderWithItem(Long itemId, long priceSnapshot, int qty) {
        Order order = mock(Order.class);
        OrderItem item = mock(OrderItem.class);
        given(item.getId()).willReturn(itemId);
        given(item.getPriceSnapshot()).willReturn(priceSnapshot);
        given(item.getQuantity()).willReturn(qty);
        given(order.getOrderItems()).willReturn(List.of(item));
        return order;
    }

    private Refund mockRefund(Long id) {
        Refund refund = mock(Refund.class);
        given(refund.getId()).willReturn(id);
        return refund;
    }
}
