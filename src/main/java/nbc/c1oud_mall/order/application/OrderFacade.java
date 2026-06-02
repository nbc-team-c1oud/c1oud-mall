package nbc.c1oud_mall.order.application;

import lombok.RequiredArgsConstructor;
import nbc.c1oud_mall.auth.application.Service.UserService;
import nbc.c1oud_mall.auth.domain.entity.User;
import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.common.exception.ErrorCode;
import nbc.c1oud_mall.order.application.dto.GetOrderItemPreviewResponse;
import nbc.c1oud_mall.order.application.dto.GetOrderPreviewResponse;
import nbc.c1oud_mall.order.application.dto.OrderCheckoutRequest;
import nbc.c1oud_mall.order.application.dto.OrderCheckoutResponse;
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

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderFacade {

    private final UserService userService;
    private final OrderService orderService;
    private final OMockCartService oMockCartService;
    private final OMockPaymentService oMockPaymentService;

    String payment;

    //주문서 미리보기 : 재고 차감/주문 생성 없는 읽기 전용
    public GetOrderPreviewResponse getOrderPreview(Long userId, List<Long> cartItemsIds) {

        //cartItems가 null/비어있으면 전체 장바구니, 값이 있으면 선택된 아이템만 주문서에 담음
        List<OMockCartItem> cartItems = getValidateCartItems(
                userId, cartItemsIds != null ? cartItemsIds : List.of()
        );

        //장바구니 아이템에서 상품 가격과 장바구니 수량을 곱해서 각 아이템의 총액 구함
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

        //장바구니 총액 구하기
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
        List<OMockCartItem> cartItems = getValidateCartItems(userId, cartItemIds);

        //2~3. 재고차감 + 스냅샷 OrderItem 생성
        List<OrderItem> orderItems = new ArrayList<>();

        for (OMockCartItem cartItem : cartItems) {
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
        List<Long> orderedItemIds = cartItems.stream().map(OMockCartItem::getId).toList();
        oMockCartService.clearCartItems(orderedItemIds, userId);

        //7. 응답
        return new OrderCheckoutResponse(
                order.getId(),
                payment,
                order.getOrderNumber(),
                order.getOrderName(),
                order.getOrderStatus().name(),
                totalPrice
        );
    }

    //CartItemTestEntity 임시
    private List<OMockCartItem> getValidateCartItems(Long userId, List<Long> cartItemsIds) {
        // cartItemsIds이 비어있으면 "전체 장바구니"
        List<OMockCartItem> cartItems = cartItemsIds.isEmpty()
                ? oMockCartService.findCartEntities(userId)
                : oMockCartService.findCartEntitiesByIds(userId, cartItemsIds);

        // 1차 검증 : 주문할 아이템이 하나도 없으면 주문서 자체 미성립
        // (전체 조회: 빈 장바구니 / 선택 조회: 넘긴 ID가 전부 남의 것/없는 것 일때도 여기로 떨어짐)

        if (cartItems.isEmpty()) {
            throw new BusinessException(ErrorCode.CART_EMPTY);
        }

        // 2차 검증 : 요청한 ID 개수와 조회된 개수가 다르다 -> 일부가 "남의 것" 또는 "존재하지 않는 ID"다.
        if (!cartItemsIds.isEmpty() && cartItems.size() != cartItemsIds.size()) {
            throw new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND);
        }

        return cartItems;
    }
}
