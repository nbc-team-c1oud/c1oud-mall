package nbc.c1oud_mall.order.application;

import nbc.c1oud_mall.auth.application.Service.UserService;
import nbc.c1oud_mall.auth.domain.entity.User;
import nbc.c1oud_mall.cart.application.CartService;
import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.order.domain.Order;
import nbc.c1oud_mall.order.domain.OrderItem;
import nbc.c1oud_mall.payment.application.PaymentInitiationService;
import nbc.c1oud_mall.payment.application.PaymentQueryService;
import nbc.c1oud_mall.product.application.ProductService;
import nbc.c1oud_mall.product.domain.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderFacadeTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long ORDER_ID = 10L;
    private static final Long PRODUCT_ID = 100L;
    private static final Integer QUANTITY = 2;

    @Mock
    private UserService userService;

    @Mock
    private OrderService orderService;

    @Mock
    private CartService cartService;

    @Mock
    private ProductService productService;

    @Mock
    private PaymentInitiationService paymentInitiationService;

    @Mock
    private PaymentQueryService paymentQueryService;

    @Mock
    private OrderCancelService orderCancelService;

    @InjectMocks
    private OrderFacade orderFacade;

    @Test
    @DisplayName("본인 주문이 아니면 주문 취소를 할 수 없다")
    void cancelOrder_notOwner_throwsException() {
        // given
        Order order = mock(Order.class);
        User user = mock(User.class);

        given(orderService.findOrderEntity(ORDER_ID)).willReturn(order);
        given(order.getUser()).willReturn(user);
        given(user.getId()).willReturn(OTHER_USER_ID);

        // when & then
        assertThatThrownBy(() -> orderFacade.cancelOrder(USER_ID, ORDER_ID))
                .isInstanceOf(BusinessException.class);

        verify(orderService, never()).cancelPendingOrder(anyLong());
        verify(orderCancelService, never()).cancelPendingPayment(anyLong());
        verify(productService, never()).restoreStockWithLock(anyLong(), anyInt());
    }

    @Test
    @DisplayName("주문 취소 성공 시 결제 실패 처리 후 재고를 복구한다")
    void cancelOrder_success() {
        // given
        Order order = mock(Order.class);
        User user = mock(User.class);
        OrderItem orderItem = mock(OrderItem.class);
        Product product = mock(Product.class);

        given(orderService.findOrderEntity(ORDER_ID)).willReturn(order);

        given(order.getId()).willReturn(ORDER_ID);
        given(order.getUser()).willReturn(user);
        given(user.getId()).willReturn(USER_ID);

        given(orderService.cancelPendingOrder(ORDER_ID)).willReturn(true);
        given(orderCancelService.cancelPendingPayment(ORDER_ID)).willReturn(true);

        given(order.getOrderItems()).willReturn(List.of(orderItem));
        given(orderItem.getProduct()).willReturn(product);
        given(product.getId()).willReturn(PRODUCT_ID);
        given(orderItem.getQuantity()).willReturn(QUANTITY);

        // when
        orderFacade.cancelOrder(USER_ID, ORDER_ID);

        // then
        verify(orderService).cancelPendingOrder(ORDER_ID);
        verify(orderCancelService).cancelPendingPayment(ORDER_ID);
        verify(productService).restoreStockWithLock(PRODUCT_ID, QUANTITY);
    }

    @Test
    @DisplayName("주문 취소가 이미 처리된 경우 결제 실패 처리와 재고 복구를 하지 않는다")
    void cancelOrder_alreadyCancelled_doNothing() {
        // given
        Order order = mock(Order.class);
        User user = mock(User.class);

        given(orderService.findOrderEntity(ORDER_ID)).willReturn(order);
        given(order.getId()).willReturn(ORDER_ID);
        given(order.getUser()).willReturn(user);
        given(user.getId()).willReturn(USER_ID);

        given(orderService.cancelPendingOrder(ORDER_ID)).willReturn(false);

        // when
        orderFacade.cancelOrder(USER_ID, ORDER_ID);

        // then
        verify(orderService).cancelPendingOrder(ORDER_ID);
        verify(orderCancelService, never()).cancelPendingPayment(anyLong());
        verify(productService, never()).restoreStockWithLock(anyLong(), anyInt());
    }

    @Test
    @DisplayName("Payment 취소가 이미 처리된 경우 재고 복구를 하지 않는다")
    void cancelOrder_paymentAlreadyFailed_doNotRestoreStock() {
        // given
        Order order = mock(Order.class);
        User user = mock(User.class);

        given(orderService.findOrderEntity(ORDER_ID)).willReturn(order);
        given(order.getId()).willReturn(ORDER_ID);
        given(order.getUser()).willReturn(user);
        given(user.getId()).willReturn(USER_ID);

        given(orderService.cancelPendingOrder(ORDER_ID)).willReturn(true);
        given(orderCancelService.cancelPendingPayment(ORDER_ID)).willReturn(false);

        // when
        orderFacade.cancelOrder(USER_ID, ORDER_ID);

        // then
        verify(orderService).cancelPendingOrder(ORDER_ID);
        verify(orderCancelService).cancelPendingPayment(ORDER_ID);
        verify(productService, never()).restoreStockWithLock(anyLong(), anyInt());
    }

    @Test
    @DisplayName("주문 상품이 여러 개면 각 상품 수량만큼 재고를 복구한다")
    void cancelOrder_success_restoreMultipleStocks() {
        // given
        Order order = mock(Order.class);
        User user = mock(User.class);

        OrderItem orderItem1 = mock(OrderItem.class);
        Product product1 = mock(Product.class);

        OrderItem orderItem2 = mock(OrderItem.class);
        Product product2 = mock(Product.class);

        given(orderService.findOrderEntity(ORDER_ID)).willReturn(order);

        given(order.getId()).willReturn(ORDER_ID);
        given(order.getUser()).willReturn(user);
        given(user.getId()).willReturn(USER_ID);

        given(orderService.cancelPendingOrder(ORDER_ID)).willReturn(true);
        given(orderCancelService.cancelPendingPayment(ORDER_ID)).willReturn(true);

        given(order.getOrderItems()).willReturn(List.of(orderItem1, orderItem2));

        given(orderItem1.getProduct()).willReturn(product1);
        given(product1.getId()).willReturn(100L);
        given(orderItem1.getQuantity()).willReturn(2);

        given(orderItem2.getProduct()).willReturn(product2);
        given(product2.getId()).willReturn(200L);
        given(orderItem2.getQuantity()).willReturn(3);

        // when
        orderFacade.cancelOrder(USER_ID, ORDER_ID);

        // then
        verify(productService).restoreStockWithLock(100L, 2);
        verify(productService).restoreStockWithLock(200L, 3);
    }
}