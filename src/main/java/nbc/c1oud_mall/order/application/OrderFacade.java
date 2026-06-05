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
import nbc.c1oud_mall.payment.application.PaymentQueryService;
import nbc.c1oud_mall.payment.application.dto.PaymentInitiationResult;
import nbc.c1oud_mall.payment.application.dto.PaymentSummary;
import nbc.c1oud_mall.payment.application.dto.command.PaymentInitiationCommand;
import nbc.c1oud_mall.product.application.ProductService;
import nbc.c1oud_mall.product.domain.Product;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderFacade {

    private final UserService userService;
    private final OrderService orderService;
    private final CartService cartService;
    private final ProductService productService;
    private final PaymentInitiationService paymentInitiationService;
    private final PaymentQueryService paymentQueryService;

    public GetOrderPreviewResponse getOrderPreview(Long userId, List<Long> cartItemsIds) {
        // CartService의 검증 메서드 호출
        List<CartItem> cartItems = cartService.getValidatedCartItemsForOrder(
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
        // CartService의 검증 메서드 호출
        List<CartItem> cartItems = cartService.getValidatedCartItemsForOrder(userId, cartItemIds);

        //3. 데드락 발생 방지를 위해 우선 정렬
        cartItems.sort(Comparator.comparing(cartItem -> cartItem.getProduct().getId()));

        //4. 재고차감 + 스냅샷 OrderItem 생성
        //product에서 비관락 설정
        List<OrderItem> orderItems = new ArrayList<>();

        for (CartItem cartItem : cartItems) {
            Product product = productService.deductStockWithLock(
                    cartItem.getProduct().getId(),
                    cartItem.getQuantity());

            OrderItem orderItem = new OrderItem(
                    product,
                    product.getName(),
                    product.getPrice(),
                    cartItem.getQuantity()
            );
            orderItems.add(orderItem);
        }
        Long totalPrice = orderItems.stream().mapToLong(OrderItem::getSubtotal).sum();

        //5. 포인트 사용 가능 여부 최종 검증
        validatePointUsage(user, pointUsedAmount, totalPrice);

        Long pgAmount = totalPrice - pointUsedAmount;

        //6. 주문 저장
        Order order = orderService.createOrder(user, totalPrice, orderItems);

        //7. 결제 사전등록 (portonePaymentId 채번)
        PaymentInitiationResult initiation = paymentInitiationService.initiate(
                new PaymentInitiationCommand(
                        order.getId(),
                        userId,
                        totalPrice,         // totalAmount (사용자가 결제할 총액)
                        pgAmount,           // pgAmount (PG에 청구할 금액 = totalAmount - pointUsedAmount)
                        pointUsedAmount     // pointUsedAmount (포인트 도입 시 분리)
                )
        );

        //8. 주문한 장바구니 아이템만 삭제 (결제에서 진행)
        //List<Long> orderedItemIds = cartItems.stream().map(CartItem::getId).toList();
        //cartService.clearCartItems(userId, orderedItemIds);

        //9. 응답
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

    public List<OrderResponse> getOrdersMe(Long userId) {
        List<Order> orders = orderService.findOrderEntities(userId);

        if (orders.isEmpty()) {
            return List.of();
        }

        List<Long> orderIds = orders.stream().map(Order::getId).toList();

        Map<Long, PaymentSummary> paymentSummaryMap = paymentQueryService.getPaymentSummaryMapByOrderIds(orderIds);

        return orders.stream()
                .map(order -> orderService.toResponse(order, getPaymentSummary(paymentSummaryMap, order.getId()))).toList();
    }

    private PaymentSummary getPaymentSummary(Map<Long, PaymentSummary> paymentSummaryMap, Long orderId) {
        PaymentSummary paymentSummary = paymentSummaryMap.get(orderId);

        if (paymentSummary == null) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_FOUND);
        }

        return paymentSummary;
    }

    public OrderResponse getOrder(Long userId, Long orderId) {
        Order order = orderService.findOrderEntity(orderId);
        if (!order.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }

        PaymentSummary paymentSummary = paymentQueryService.getPaymentSummaryByOrderId(orderId).orElseThrow(
                () -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        return orderService.toResponse(order, paymentSummary);
    }

}
