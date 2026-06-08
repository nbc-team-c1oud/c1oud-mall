package nbc.c1oud_mall.refund.application;

import lombok.RequiredArgsConstructor;
import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.common.exception.ErrorCode;
import nbc.c1oud_mall.order.domain.Order;
import nbc.c1oud_mall.order.domain.OrderItem;
import nbc.c1oud_mall.order.infrastructure.OrderJpaRepository;
import nbc.c1oud_mall.payment.domain.Payment;
import nbc.c1oud_mall.payment.infrastructure.PaymentJpaRepository;
import nbc.c1oud_mall.point.application.PointService;
import nbc.c1oud_mall.product.application.ProductService;
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
import java.util.Comparator;
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
    private final ProductService productService;
    private final PointService pointService;

    /**
     * 비관적 락 획득 → 잔여 수량 재검증 → Refund 저장(DB_COMMITTED) → 포인트/재고 복구.
     * 단일 @Transactional로 묶어 중간 실패 시 전체 롤백.
     * 락 순서(consistency.md §5): Payment → Point → Inventory.
     * 재고 복구는 ProductService.restoreStockWithLock per item (productId 정렬로 데드락 방지).
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
                payment.getBreakdown().getPointUsedAmount(),
                payment.getPointEarnedAmount(),
                payment.getPortonePaymentId());

        Refund refund = Refund.of(rp, lockedRequests, breakdown, command.reason());
        refund.markDbCommitted();
        refundJpaRepository.save(refund);

        // 5. 포인트 사용분 환원 (consistency.md §5: Inventory보다 먼저)
        if (breakdown.getPointRefundAmount() > 0) {
            pointService.restorePoints(command.userId(),
                    breakdown.getPointRefundAmount(), payment);
        }

        // 5-2. 적립 포인트 비례 회수 (잔액 부족 시 잔액까지만 — lenient)
        if (breakdown.getPointEarnedRecoverAmount() > 0) {
            pointService.cancelEarnedPoints(command.userId(),
                    breakdown.getPointEarnedRecoverAmount(), payment);
        }

        // 6. 재고 복구 (productId 정렬 → 비관락 단위 호출, 데드락 방지)
        lockedRequests.stream()
                .sorted(Comparator.comparingLong(
                        req -> itemMap.get(req.orderItemId()).getProductId()))
                .forEach(req -> productService.restoreStockWithLock(
                        itemMap.get(req.orderItemId()).getProductId(),
                        req.quantity()));

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
