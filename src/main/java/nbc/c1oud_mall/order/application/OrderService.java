package nbc.c1oud_mall.order.application;

import lombok.RequiredArgsConstructor;
import nbc.c1oud_mall.auth.domain.entity.User;
import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.common.exception.ErrorCode;
import nbc.c1oud_mall.order.application.dto.OrderByOrderIdResponse;
import nbc.c1oud_mall.order.application.dto.OrderItemResponse;
import nbc.c1oud_mall.order.application.dto.OrderResponse;
import nbc.c1oud_mall.order.domain.Order;
import nbc.c1oud_mall.order.domain.OrderItem;
import nbc.c1oud_mall.order.infrastructure.OrderJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderJpaRepository orderJpaRepository;

    //주문 생성
    @Transactional
    public Order createOrder(User user, Long totalPrice, List<OrderItem> orderItems) {
        Order order = new Order(user, totalPrice, orderItems );
        return orderJpaRepository.save(order);
    }

    //내 주문 조회 (최신순)
    public List<Order> findOrderEntities(Long userId) {
        return orderJpaRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    //주문 단건 상세 조회
    public Order findOrderEntity(Long orderId) {
        return orderJpaRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }

    // Order -> OrderResponse 변환
    public OrderResponse toResponse(Order order, Long paymentId) {
        List<OrderItemResponse> items = order.getOrderItems().stream()
                .map(oi -> new OrderItemResponse(
                        oi.getProductNameSnapshot(),
                        oi.getPriceSnapshot(),
                        oi.getQuantity()
                )).toList();

        return new OrderResponse(
                order.getId(),
                paymentId,
                order.getOrderNumber(),
                order.getOrderStatus().name(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                items
        );
    }

    //포인트 관련 추가 예정
    public OrderByOrderIdResponse toOrderResponse(Order order, Long paymentId) {
        List<OrderItemResponse> items = order.getOrderItems().stream()
                .map(oi -> new OrderItemResponse(
                        oi.getProductNameSnapshot(),
                        oi.getPriceSnapshot(),
                        oi.getQuantity()
                )).toList();

        return new OrderByOrderIdResponse(
                order.getId(),
                paymentId,
                order.getOrderNumber(),
                order.getOrderStatus().name(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                items
        );
    }
}
