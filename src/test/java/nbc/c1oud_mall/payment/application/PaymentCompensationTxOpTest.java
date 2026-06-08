package nbc.c1oud_mall.payment.application;

import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.common.exception.ErrorCode;
import nbc.c1oud_mall.order.application.OrderService;
import nbc.c1oud_mall.order.domain.Order;
import nbc.c1oud_mall.order.domain.OrderItem;
import nbc.c1oud_mall.payment.domain.Payment;
import nbc.c1oud_mall.payment.domain.PaymentStatus;
import nbc.c1oud_mall.payment.infrastructure.PaymentJpaRepository;
import nbc.c1oud_mall.product.application.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentCompensationTxOpTest {

    private static final String PORTONE_ID = "portone-compensate-001";
    private static final Long ORDER_ID = 10L;
    private static final Long USER_ID = 100L;
    private static final String REASON = "amount mismatch";

    @Mock
    private PaymentJpaRepository paymentRepository;
    @Mock
    private OrderService orderService;
    @Mock
    private ProductService productService;

    @InjectMocks
    private PaymentCompensationTxOp txOp;

    @Test
    @DisplayName("정상 보상: Order 로드 → markFailed → productId 정렬 후 restoreStockWithLock × N → cancelOrder")
    void compensate_restores_stock_per_item_in_product_id_order() {
        Payment payment = Payment.rehydrate(
                42L, PORTONE_ID, ORDER_ID, USER_ID,
                10_000L, 9_000L, 1_000L, 0L,
                PaymentStatus.PENDING, null, null);

        OrderItem item3 = mockOrderItem(3L, 1);
        OrderItem item1 = mockOrderItem(1L, 2);
        OrderItem item2 = mockOrderItem(2L, 5);
        Order order = mock(Order.class);
        given(order.getOrderItems()).willReturn(List.of(item3, item1, item2)); // 일부러 unsorted

        given(paymentRepository.findByPortonePaymentId(PORTONE_ID))
                .willReturn(Optional.of(payment));
        given(orderService.findOrderEntity(ORDER_ID)).willReturn(order);

        txOp.compensateDb(PORTONE_ID, REASON);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);

        InOrder seq = inOrder(orderService, productService);
        seq.verify(orderService).findOrderEntity(ORDER_ID);
        seq.verify(productService).restoreStockWithLock(eq(1L), eq(2));   // 정렬 후 1번
        seq.verify(productService).restoreStockWithLock(eq(2L), eq(5));   // 정렬 후 2번
        seq.verify(productService).restoreStockWithLock(eq(3L), eq(1));   // 정렬 후 3번
        seq.verify(orderService).cancelOrder(ORDER_ID);
    }

    @Test
    @DisplayName("Payment 없음 → BusinessException(PAYMENT_NOT_FOUND), 후속 호출 없음")
    void compensate_throws_when_payment_not_found() {
        given(paymentRepository.findByPortonePaymentId(PORTONE_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> txOp.compensateDb(PORTONE_ID, REASON))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);

        verify(orderService, never()).findOrderEntity(anyLongValue());
        verify(productService, never()).restoreStockWithLock(anyLongValue(), anyIntValue());
        verify(orderService, never()).cancelOrder(anyLongValue());
    }

    @Test
    @DisplayName("OrderItem이 비어있어도 markFailed + cancelOrder는 호출, restoreStockWithLock 미호출")
    void compensate_handles_empty_order_items() {
        Payment payment = Payment.rehydrate(
                42L, PORTONE_ID, ORDER_ID, USER_ID,
                10_000L, 9_000L, 1_000L, 0L,
                PaymentStatus.PENDING, null, null);

        Order order = mock(Order.class);
        given(order.getOrderItems()).willReturn(List.of());

        given(paymentRepository.findByPortonePaymentId(PORTONE_ID))
                .willReturn(Optional.of(payment));
        given(orderService.findOrderEntity(ORDER_ID)).willReturn(order);

        txOp.compensateDb(PORTONE_ID, REASON);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(productService, never()).restoreStockWithLock(anyLongValue(), anyIntValue());
        verify(orderService).cancelOrder(ORDER_ID);
    }

    // ── 헬퍼 ──

    private OrderItem mockOrderItem(Long productId, int quantity) {
        OrderItem item = mock(OrderItem.class);
        given(item.getProductId()).willReturn(productId);
        given(item.getQuantity()).willReturn(quantity);
        return item;
    }

    private static Long anyLongValue() {
        return org.mockito.ArgumentMatchers.anyLong();
    }

    private static Integer anyIntValue() {
        return org.mockito.ArgumentMatchers.anyInt();
    }
}
