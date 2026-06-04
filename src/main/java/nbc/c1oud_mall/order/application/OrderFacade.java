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
import nbc.c1oud_mall.payment.application.PaymentInitiationService;
import nbc.c1oud_mall.payment.application.dto.PaymentInitiationResult;
import nbc.c1oud_mall.payment.application.dto.command.PaymentInitiationCommand;
import nbc.c1oud_mall.product.domain.Product;
import nbc.c1oud_mall.product.infrastructure.ProductJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderFacade {

    private final UserService userService;
    private final OrderService orderService;
    private final CartService cartService;
    private final ProductJpaRepository productJpaRepository;
    private final PaymentInitiationService paymentInitiationService;

    public GetOrderPreviewResponse getOrderPreview(Long userId, List<Long> cartItemsIds) {

        List<CartItem> cartItems = getValidateCartItems(
                userId, cartItemsIds != null ? cartItemsIds : List.of()
        );

        List<GetOrderItemPreviewResponse> items = cartItems.stream()
                .map(cartItem -> {
                    Long price = cartItem.getProduct().getPrice();
                    Long subtotal = price * cartItem.getQuantity();
                    return new GetOrderItemPreviewResponse(
                            cartItem.getProduct().getId(),
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
        Long pointUsedAmount = request != null && request.getPointUsedAmount() != null ?
                request.getPointUsedAmount() : 0L;

        //0. 포인트 입력값 검증
        validatePointAmountFormat(pointUsedAmount);

        //1. 회원조회
        User user = userService.findById(userId);

        //2. 장바구니 조회 (선택된 아이템만) 임시 엔티티
        List<CartItem> cartItems = getValidateCartItems(userId, cartItemIds);

        //3. 데드락 발생 방지를 위해 우선 정렬
        cartItems.sort(Comparator.comparing(cartItem -> cartItem.getProduct().getId()));

        //4. 재고차감 + 스냅샷 OrderItem 생성
        //product에서 비관락 설정
        List<OrderItem> orderItems = new ArrayList<>();

        for (CartItem cartItem : cartItems) {
            Product product = productJpaRepository.findByIdForUpdate(cartItem.getProduct().getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
            product.deductStock(cartItem.getQuantity());

            OrderItem orderItem = new OrderItem(
                    product,
                    product.getName(),
                    product.getPrice(),
                    cartItem.getQuantity()
            );
            orderItems.add(orderItem);
        }
        Long totalPrice = orderItems.stream().mapToLong(OrderItem::getSubtotal).sum();

        //4. 포인트 사용 가능 여부 최종 검증
        validatePointUsage(user, pointUsedAmount, totalPrice);

        Long pgAmount = totalPrice - pointUsedAmount;

        //5. 주문 저장
        Order order = orderService.createOrder(user, totalPrice, orderItems);

        //6. 결제 사전등록 (portonePaymentId 채번)
        PaymentInitiationResult initiation = paymentInitiationService.initiate(
                new PaymentInitiationCommand(
                        order.getId(),
                        userId,
                        totalPrice,         // totalAmount (사용자가 결제할 총액)
                        pgAmount,           // pgAmount (PG에 청구할 금액 = totalAmount - pointUsedAmount)
                        pointUsedAmount     // pointUsedAmount (포인트 도입 시 분리)
                )
        );

        //7. 주문한 장바구니 아이템만 삭제 (결제에서 진행)
        //List<Long> orderedItemIds = cartItems.stream().map(CartItem::getId).toList();
        //cartService.clearCartItems(userId, orderedItemIds);

        //8. 응답
        return new OrderCheckoutResponse(
                order.getId(),
                initiation.portonePaymentId(),
                order.getOrderNumber(),
                order.getOrderName(),
                order.getOrderStatus().name(),
                totalPrice,
                pgAmount,
                pointUsedAmount
        );
    }

    private void validatePointAmountFormat(Long pointUsedAmount) {
        if (pointUsedAmount < 0) {
            throw new BusinessException(ErrorCode.POINT_AMOUNT_INVALID);
        }
    }

    private void validatePointUsage(User user, Long pointUsedAmount, Long totalPrice) {
        // 총 금액보다 더 많은 포인트 사용 시 에러
        if (pointUsedAmount > totalPrice) {
            throw new BusinessException(ErrorCode.POINT_AMOUNT_INVALID);
        }

        //보유 포인트 부족시 에러
        if (user.getPointBalance() < pointUsedAmount) {
            throw new BusinessException(ErrorCode.POINT_INSUFFICIENT);
        }
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
