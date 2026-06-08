package nbc.c1oud_mall.order.application;

import lombok.RequiredArgsConstructor;
import nbc.c1oud_mall.auth.domain.entity.User;
import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.common.exception.ErrorCode;
import nbc.c1oud_mall.order.application.dto.OrderItemResponse;
import nbc.c1oud_mall.order.application.dto.OrderResponse;
import nbc.c1oud_mall.order.domain.Order;
import nbc.c1oud_mall.order.domain.OrderItem;
import nbc.c1oud_mall.order.domain.OrderStatus;
import nbc.c1oud_mall.order.infrastructure.OrderJpaRepository;
import nbc.c1oud_mall.payment.application.dto.PaymentSummary;
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

    // Order -> OrderResponse
    public OrderResponse toResponse(Order order, PaymentSummary paymentSummary) {
        List<OrderItemResponse> items = order.getOrderItems().stream()
                .map(OrderItemResponse::from).toList();

        return new OrderResponse(
                order.getId(),
                paymentSummary.paymentId(),
                paymentSummary.paymentStatus(),
                order.getOrderNumber(),
                order.getOrderStatus().name(),
                order.getTotalAmount(),
                paymentSummary.pgAmount(),
                paymentSummary.pointUsedAmount(),
                paymentSummary.pointEarnedAmount(),
                order.getCreatedAt(),
                items
        );
    }

    /**
     * 결제 확정 시 payment BC에서 호출.
     * - 호출자(payment) 트랜잭션에 참여 (PROPAGATION.REQUIRED, 기본값)
     * - 멱등: 이미 CONFIRMED 상태면 silent OK (아무 일도 하지 않고 정상 반환)
     *
     * @param orderId 확정할 주문 ID
     * @throws BusinessException orderId에 해당하는 주문이 없을 때 / ORDER_NOT_FOUND
     * @throws BusinessException PENDING_PAYMENT가 아닌 상태에서 호출됐을 때 / INVALID_ORDER_STATUS (CANCELLED 등)
     */
    @Transactional   // readOnly=true 클래스 어노테이션을 메서드에서 오버라이드
    public void completeOrder(Long orderId) {
        Order order = findOrderById(orderId);

        if (order.getOrderStatus() == OrderStatus.CONFIRMED) {
            return;   // 이미 확정 — silent OK (idempotency 규칙 §5)
        }
        order.markAsConfirmed();   // 내부 가드에 의해 OD002 던질 가능성 있음
    }

    /**
     * 결제 보상 트랜잭션에서 payment BC가 호출 (REQUIRES_NEW TX 안).
     * - 호출자(payment 보상) 트랜잭션에 참여
     * - 멱등: 이미 CANCELLED 상태면 silent OK
     */
    @Transactional
    public void cancelOrder(Long orderId) {
        Order order = findOrderById(orderId);

        if (order.getOrderStatus() == OrderStatus.CANCELLED) {
            return;   // 이미 취소 — silent OK
        }
        order.markAsCancelled();
    }

    @Transactional
    public boolean cancelPendingOrder(Long orderId) {
        Order order = findOrderById(orderId);

        if (order.getOrderStatus() == OrderStatus.CANCELLED) {
            return false;   // 이미 취소 — silent OK
        }

        if (order.getOrderStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
        }

        order.markAsCancelled();
        return true;
    }

    private Order findOrderById(Long orderId) {
        return orderJpaRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }
}
