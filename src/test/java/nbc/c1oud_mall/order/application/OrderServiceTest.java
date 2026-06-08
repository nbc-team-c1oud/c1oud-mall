package nbc.c1oud_mall.order.application;

import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.common.exception.ErrorCode;
import nbc.c1oud_mall.order.domain.Order;
import nbc.c1oud_mall.order.domain.OrderStatus;
import nbc.c1oud_mall.order.infrastructure.OrderJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Long ORDER_ID = 1L;

    @Mock
    private OrderJpaRepository orderJpaRepository;

    @InjectMocks
    private OrderService orderService;

    @Test
    @DisplayName("결제대기 주문을 취소하면 CANCELLED 상태가 되고 true를 반환한다")
    void cancelPendingOrder_success() {
        // given
        Order order = pendingOrder();

        given(orderJpaRepository.findById(ORDER_ID))
                .willReturn(Optional.of(order));

        // when
        boolean result = orderService.cancelPendingOrder(ORDER_ID);

        // then
        assertThat(result).isTrue();
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("이미 취소된 주문을 취소하면 false를 반환한다")
    void cancelPendingOrder_alreadyCancelled_returnFalse() {
        // given
        Order order = cancelledOrder();

        given(orderJpaRepository.findById(ORDER_ID))
                .willReturn(Optional.of(order));

        // when
        boolean result = orderService.cancelPendingOrder(ORDER_ID);

        // then
        assertThat(result).isFalse();
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("결제완료 주문은 결제대기 주문 취소 API로 취소할 수 없다")
    void cancelPendingOrder_confirmed_throwsException() {
        // given
        Order order = confirmedOrder();

        given(orderJpaRepository.findById(ORDER_ID))
                .willReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> orderService.cancelPendingOrder(ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_ORDER_STATUS);
    }

    @Test
    @DisplayName("존재하지 않는 주문을 취소하려 하면 ORDER_NOT_FOUND 예외가 발생한다")
    void cancelPendingOrder_notFound_throwsException() {
        // given
        given(orderJpaRepository.findById(ORDER_ID))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> orderService.cancelPendingOrder(ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("결제대기 주문을 결제 확정 처리하면 CONFIRMED 상태가 된다")
    void completeOrder_success() {
        // given
        Order order = pendingOrder();

        given(orderJpaRepository.findById(ORDER_ID))
                .willReturn(Optional.of(order));

        // when
        orderService.completeOrder(ORDER_ID);

        // then
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("이미 결제확정된 주문은 다시 확정해도 상태를 유지한다")
    void completeOrder_alreadyConfirmed_doNothing() {
        // given
        Order order = confirmedOrder();

        given(orderJpaRepository.findById(ORDER_ID))
                .willReturn(Optional.of(order));

        // when
        orderService.completeOrder(ORDER_ID);

        // then
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("취소된 주문은 결제 확정할 수 없다")
    void completeOrder_cancelled_throwsException() {
        // given
        Order order = cancelledOrder();

        given(orderJpaRepository.findById(ORDER_ID))
                .willReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> orderService.completeOrder(ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_ORDER_STATUS);
    }

    @Test
    @DisplayName("결제 보상 취소 호출 시 결제대기 주문은 CANCELLED 상태가 된다")
    void cancelOrder_pending_success() {
        // given
        Order order = pendingOrder();

        given(orderJpaRepository.findById(ORDER_ID))
                .willReturn(Optional.of(order));

        // when
        orderService.cancelOrder(ORDER_ID);

        // then
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("결제 보상 취소 호출 시 이미 취소된 주문은 상태를 유지한다")
    void cancelOrder_alreadyCancelled_doNothing() {
        // given
        Order order = cancelledOrder();

        given(orderJpaRepository.findById(ORDER_ID))
                .willReturn(Optional.of(order));

        // when
        orderService.cancelOrder(ORDER_ID);

        // then
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    private Order pendingOrder() {
        return new Order(
                null,
                30_000L,
                List.of()
        );
    }

    private Order confirmedOrder() {
        Order order = pendingOrder();
        order.markAsConfirmed();
        return order;
    }

    private Order cancelledOrder() {
        Order order = pendingOrder();
        order.markAsCancelled();
        return order;
    }
}