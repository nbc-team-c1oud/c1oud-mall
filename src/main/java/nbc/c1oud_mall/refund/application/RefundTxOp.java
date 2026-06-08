package nbc.c1oud_mall.refund.application;

import lombok.RequiredArgsConstructor;
import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.common.exception.ErrorCode;
import nbc.c1oud_mall.order.domain.Order;
import nbc.c1oud_mall.order.domain.OrderItem;
import nbc.c1oud_mall.order.infrastructure.OrderJpaRepository;
import nbc.c1oud_mall.payment.domain.Payment;
import nbc.c1oud_mall.payment.infrastructure.PaymentJpaRepository;
import nbc.c1oud_mall.refund.application.dto.command.RefundCommand;
import nbc.c1oud_mall.refund.application.dto.command.RefundItemCommand;
import nbc.c1oud_mall.refund.domain.Refund;
import nbc.c1oud_mall.refund.domain.RefundBreakdown;
import nbc.c1oud_mall.refund.domain.RefundItemRequest;
import nbc.c1oud_mall.refund.domain.RefundablePayment;
import nbc.c1oud_mall.refund.infrastructure.RefundJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RefundTxOp {

    private final RefundJpaRepository refundJpaRepository;
    private final PaymentJpaRepository paymentJpaRepository;
    private final OrderJpaRepository orderJpaRepository;
    private final InventoryRestorePort inventoryRestorePort;
    private final PointRestorePort pointRestorePort;

    /**
     * 비관적 락 획득 → 잔여 수량 재검증 → Refund 저장(DB_COMMITTED) → 재고/포인트 복구.
     * 단일 @Transactional로 묶어 중간 실패 시 전체 롤백.
     * 락 내부에서 외부 호출 금지 — mock 어댑터라 실질적으로 OK, 실구현 시에도 포트 계약 준수.
     */
    @Transactional
    public Refund executeRefund(RefundCommand command, RefundBreakdown breakdown) {
        // 1. Payment 비관적 락 (consistency.md §5 락 순서: Payment 먼저)
        Payment payment = paymentJpaRepository.findByOrderIdForUpdate(command.orderId())
                .orElseThrow(() -> BusinessException.withDetail(
                        ErrorCode.PAYMENT_NOT_FOUND, "orderId=" + command.orderId()));

        // 2. Order items 조회 (동일 TX, FETCH JOIN으로 N+1 방지)
        Order order = orderJpaRepository.findByOrderId(command.orderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        Map<Long, OrderItem> itemMap = order.getOrderItems().stream()
                .collect(Collectors.toMap(OrderItem::getId, Function.identity()));

        // 3. 잔여 수량 재검증 (선검증 통과 후 race 차단 — idempotency.md §4 A등급)
        List<RefundItemRequest> lockedRequests = new ArrayList<>();
        for (RefundItemCommand itemCmd : command.items()) {
            OrderItem oi = itemMap.get(itemCmd.orderItemId());
            if (oi == null) {
                throw BusinessException.withDetail(ErrorCode.INVALID_INPUT,
                        "orderItemId=" + itemCmd.orderItemId() + " not found in order");
            }
            long sumRefunded = refundJpaRepository.sumRefundedQuantity(
                    payment.getId(), itemCmd.orderItemId());
            int remaining = oi.getQuantity() - (int) sumRefunded;
            if (itemCmd.quantity() > remaining) {
                throw BusinessException.withDetail(ErrorCode.REFUND_QUANTITY_EXCEEDED,
                        "orderItemId=" + itemCmd.orderItemId()
                                + ", requested=" + itemCmd.quantity()
                                + ", remaining=" + remaining);
            }
            lockedRequests.add(new RefundItemRequest(
                    itemCmd.orderItemId(), itemCmd.quantity(), remaining,
                    oi.getPriceSnapshot()));
        }

        // 4. Refund Aggregate 저장 (DB_COMMITTED)
        RefundablePayment rp = new RefundablePayment(
                payment.getId(), payment.getUserId(), payment.isCompleted(),
                payment.getBreakdown().getTotalAmount(), payment.getBreakdown().getPgAmount(),
                payment.getBreakdown().getPointUsedAmount(), payment.getPortonePaymentId());

        Refund refund = Refund.of(rp, lockedRequests, breakdown, command.reason());
        refund.markDbCommitted();
        refundJpaRepository.save(refund);

        // 5. 재고 복구
        inventoryRestorePort.restore(command.orderId(), lockedRequests);

        // 6. 포인트 복구 (포인트 결제 사용분이 있을 때만)
        if (breakdown.getPointRefundAmount() > 0) {
            pointRestorePort.restore(command.userId(),
                    breakdown.getPointRefundAmount(), payment.getId());
        }

        return refund;
    }

    @Transactional
    public void markPgCancelled(Long refundId, String pgCancelTxId) {
        Refund refund = refundJpaRepository.findById(refundId)
                .orElseThrow(() -> BusinessException.withDetail(
                        ErrorCode.PAYMENT_NOT_FOUND, "refundId=" + refundId));
        refund.markPgCancelled(pgCancelTxId);
    }
}
