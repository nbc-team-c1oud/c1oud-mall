package nbc.c1oud_mall.order.application;

import lombok.RequiredArgsConstructor;
import nbc.c1oud_mall.auth.application.Service.UserService;
import nbc.c1oud_mall.auth.domain.entity.User;
import nbc.c1oud_mall.cart.application.CartService;
import nbc.c1oud_mall.cart.domain.CartItem;
import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.common.exception.ErrorCode;
import nbc.c1oud_mall.order.application.dto.*;
import nbc.c1oud_mall.order.domain.Order;
import nbc.c1oud_mall.order.domain.OrderItem;
import nbc.c1oud_mall.order.infrastructure.mock.OMockCartItem;
import nbc.c1oud_mall.order.infrastructure.mock.OMockCartService;
import nbc.c1oud_mall.order.infrastructure.mock.OMockPaymentService;
import nbc.c1oud_mall.payment.domain.Payment;
import nbc.c1oud_mall.product.domain.Product;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderFacade {

    private final UserService userService;
    private final OrderService orderService;
    private final OMockCartService oMockCartService;
    private final CartService cartService;
    //private final OMockPaymentService oMockPaymentService;

    String oMockpayment;

    public GetOrderPreviewResponse getOrderPreview(Long userId, List<Long> cartItemsIds) {

        List<CartItem> cartItems = getValidateCartItems(
                userId, cartItemsIds != null ? cartItemsIds : List.of()
        );

        List<GetOrderItemPreviewResponse> items = cartItems.stream()
                .map(cartItem -> {
                    Long price = cartItem.getProduct().getPrice();
                    Long subtotal = price * cartItem.getQuantity();
                    return new GetOrderItemPreviewResponse(
                            cartItem.getId(),
                            cartItem.getProduct().getName(),
                            price,
                            cartItem.getQuantity(),
                            subtotal
                    );

                })
                .toList();

        Long totalPrice = items.stream()
                .mapToLong(GetOrderItemPreviewResponse::getSubtotal)
                .sum();

        return new GetOrderPreviewResponse(items, totalPrice);
    }

    @Transactional
    public OrderCheckoutResponse createOrder(Long userId, OrderCheckoutRequest request) {
        List<Long> cartItemIds = (request != null) ? request.getCartItemIds() : List.of();

        //0. 회원조회
        User user = userService.findById(userId);

        //1. 장바구니 조회 (선택된 아이템만) 임시 엔티티
        List<CartItem> cartItems = getValidateCartItems(userId, cartItemIds);

        //2~3. 재고차감 + 스냅샷 OrderItem 생성
        List<OrderItem> orderItems = new ArrayList<>();

        for (CartItem cartItem : cartItems) {
            Product product = cartItem.getProduct();
            product.deduckStock(cartItem.getQuantity());

            OrderItem orderItem = new OrderItem(
                    product,
                    product.getName(),
                    product.getPrice(),
                    product.getStockQuantity()
            );
            orderItems.add(orderItem);
        }
        Long totalPrice = orderItems.stream().mapToLong(OrderItem::getSubtotal).sum();

        //4. 주문 저장
        Order order = orderService.createOrder(user, totalPrice, orderItems);

        //5. 결제 정보 생성


        //6. 주문한 장바구니 아이템만 삭제
        List<Long> orderedItemIds = cartItems.stream().map(CartItem::getId).toList();
        oMockCartService.clearCartItems(orderedItemIds, userId);

        //7. 응답
        return new OrderCheckoutResponse(
                order.getId(),
                oMockpayment,
                order.getOrderNumber(),
                order.getOrderName(),
                order.getOrderStatus().name(),
                totalPrice
        );
    }

    //임시
    public List<OrderResponse> getOrdersMe(Long userId) {
        List<Order> orders = orderService.findOrderEntities(userId);
        List<Long> orderIds = orders.stream().map(Order::getId).toList();
        //결제 서비스에서 주문 목록에 결제 ID 붙이기 위한 조회 기능 추가하여 연결
        Long oMockpaymentId = 0L;

        return orders.stream()
                .map(order -> orderService.toResponse(order, oMockpaymentId)).toList();
    }

    public OrderByOrderIdResponse getOrder(Long userId, Long orderId) {
        Order order = orderService.findOrderEntity(orderId);
        if (!order.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }

        //결제 서비스에서 주문 목록에 결제 ID 붙이기 위한 조회 기능 추가하여 연결
        Long oMockpaymentId = 0L;

        return orderService.toOrderResponse(order, oMockpaymentId);
    }

    //장바구니 서비스에 넣어야함
    private List<CartItem> getValidateCartItems(Long userId, List<Long> cartItemsIds) {
        // cartItemsIds이 비어있으면 "전체 장바구니"
        List<CartItem> cartItems = cartItemsIds.isEmpty()
                ? cartService.findCartEntities(userId)
                : cartService.findCartEntitiesByIds(userId, cartItemsIds);

        if (cartItems.isEmpty()) {
            throw new BusinessException(ErrorCode.CART_EMPTY);
        }

        if (!cartItemsIds.isEmpty() && cartItems.size() != cartItemsIds.size()) {
            throw new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND);
        }

        return cartItems;
    }
}
