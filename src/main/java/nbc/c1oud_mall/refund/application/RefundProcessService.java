package nbc.c1oud_mall.refund.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.common.exception.ErrorCode;
import nbc.c1oud_mall.order.domain.Order;
import nbc.c1oud_mall.order.domain.OrderItem;
import nbc.c1oud_mall.order.infrastructure.OrderJpaRepository;
import nbc.c1oud_mall.payment.application.PortOnePaymentCancelPort;
import nbc.c1oud_mall.payment.domain.Payment;
import nbc.c1oud_mall.payment.infrastructure.PaymentJpaRepository;
import nbc.c1oud_mall.refund.application.dto.RefundResult;
import nbc.c1oud_mall.refund.application.dto.command.RefundCommand;
import nbc.c1oud_mall.refund.application.dto.command.RefundItemCommand;
import nbc.c1oud_mall.refund.domain.Refund;
import nbc.c1oud_mall.refund.domain.RefundAmountCalculator;
import nbc.c1oud_mall.refund.domain.RefundBreakdown;
import nbc.c1oud_mall.refund.domain.RefundItemRequest;
import nbc.c1oud_mall.refund.domain.RefundStatus;
import nbc.c1oud_mall.refund.domain.RefundablePayment;
import nbc.c1oud_mall.refund.infrastructure.RefundJpaRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefundProcessService {

    private final PaymentJpaRepository paymentJpaRepository;
    private final OrderJpaRepository orderJpaRepository;
    private final RefundJpaRepository refundJpaRepository;
    private final RefundAmountCalculator refundAmountCalculator;
    private final RefundTxOp refundTxOp;
    private final PortOnePaymentCancelPort portOnePaymentCancelPort;

    /**
     * 환불 처리 단일 진입점.
     * 순서: 선검증(fast-fail, 락 없음) → DB TX(비관적 락 + 재검증 + 저장) → PG 취소(TX 밖).
     * consistency.md §6: DB 커밋 후 PG 취소. 락 내부에서 외부 호출 금지.
     */
    public RefundResult process(RefundCommand command) {
        // ── Step 1: 선검증 (락 없는 fast-fail) ──
        Payment payment = paymentJpaRepository.findByOrderId(command.orderId())
                .orElseThrow(() -> BusinessException.withDetail(
                        ErrorCode.PAYMENT_NOT_FOUND, "orderId=" + command.orderId()));

        // RF003 소유권 검증
        if (!payment.getUserId().equals(command.userId())) {
            throw BusinessException.withDetail(ErrorCode.REFUND_OWNERSHIP_FAILED,
                    "paymentUserId=" + payment.getUserId()
                            + ", requestUserId=" + command.userId());
        }

        // RF002 결제 상태 검증
        if (!payment.isCompleted()) {
            throw new BusinessException(ErrorCode.REFUND_NOT_REFUNDABLE_STATE);
        }

        // RF001 잔여 수량 사전 검증 + 환불 금액 산정
        Order order = orderJpaRepository.findByOrderId(command.orderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        Map<Long, OrderItem> itemMap = order.getOrderItems().stream()
                .collect(Collectors.toMap(OrderItem::getId, Function.identity()));

        List<RefundItemRequest> preItems = buildAndValidateItems(command.items(), payment.getId(), itemMap);

        RefundablePayment rpForCalc = toRefundablePayment(payment);
        RefundBreakdown breakdown = refundAmountCalculator.calculate(rpForCalc, preItems);

        // ── Step 2: DB TX (비관적 락 + 재검증 + 저장) ──
        Refund refund = refundTxOp.executeRefund(command, breakdown);

        // ── Step 3: PG 취소 (트랜잭션 밖, consistency.md §6) ──
        if (breakdown.getPgRefundAmount() == 0) {
            // 포인트 전액 결제: PG 취소 불필요 → PG_CANCELLED로 직행
            refundTxOp.markPgCancelled(refund.getId(), null);
            return new RefundResult(refund.getId(), RefundStatus.PG_CANCELLED, breakdown);
        }

        try {
            portOnePaymentCancelPort.cancel(
                    payment.getPortonePaymentId(),
                    breakdown.getPgRefundAmount(),
                    command.reason(),
                    "refund-" + refund.getId());
            refundTxOp.markPgCancelled(refund.getId(), null);
            return new RefundResult(refund.getId(), RefundStatus.PG_CANCELLED, breakdown);
        } catch (Exception e) {
            log.error("[REFUND_PG_CANCEL_FAILED] refundId={}, portonePaymentId={}, reason={}",
                    refund.getId(), payment.getPortonePaymentId(), command.reason(), e);
        }

        return new RefundResult(refund.getId(), RefundStatus.DB_COMMITTED, breakdown);
    }

    private List<RefundItemRequest> buildAndValidateItems(List<RefundItemCommand> itemCmds,
                                                          Long paymentId,
                                                          Map<Long, OrderItem> itemMap) {
        List<RefundItemRequest> result = new ArrayList<>();
        for (RefundItemCommand itemCmd : itemCmds) {
            OrderItem oi = itemMap.get(itemCmd.orderItemId());
            if (oi == null) {
                throw BusinessException.withDetail(ErrorCode.INVALID_INPUT,
                        "orderItemId=" + itemCmd.orderItemId() + " not found");
            }
            long sumRefunded = refundJpaRepository.sumRefundedQuantity(paymentId, itemCmd.orderItemId());
            int remaining = oi.getQuantity() - (int) sumRefunded;
            if (itemCmd.quantity() > remaining) {
                throw BusinessException.withDetail(ErrorCode.REFUND_QUANTITY_EXCEEDED,
                        "orderItemId=" + itemCmd.orderItemId()
                                + ", requested=" + itemCmd.quantity()
                                + ", remaining=" + remaining);
            }
            result.add(new RefundItemRequest(
                    itemCmd.orderItemId(), itemCmd.quantity(), remaining, oi.getPriceSnapshot()));
        }
        return result;
    }

    private RefundablePayment toRefundablePayment(Payment payment) {
        return new RefundablePayment(
                payment.getId(), payment.getUserId(), payment.isCompleted(),
                payment.getBreakdown().getTotalAmount(), payment.getBreakdown().getPgAmount(),
                payment.getBreakdown().getPointUsedAmount(),
                payment.getPointEarnedAmount(),
                payment.getPortonePaymentId());
    }
}
