package nbc.c1oud_mall.payment.application;

import lombok.RequiredArgsConstructor;
import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.common.exception.ErrorCode;
import nbc.c1oud_mall.order.application.OrderService;
import nbc.c1oud_mall.order.domain.Order;
import nbc.c1oud_mall.order.domain.OrderItem;
import nbc.c1oud_mall.payment.domain.Payment;
import nbc.c1oud_mall.payment.infrastructure.PaymentJpaRepository;
import nbc.c1oud_mall.product.application.ProductService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;

@Component
@RequiredArgsConstructor
public class PaymentCompensationTxOp {

    private final PaymentJpaRepository paymentRepository;
    private final OrderService orderService;
    private final ProductService productService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void compensateDb(String portonePaymentId, String reason) {
        Payment payment = paymentRepository.findByPortonePaymentId(portonePaymentId)
                .orElseThrow(() -> BusinessException.withDetail(
                        ErrorCode.PAYMENT_NOT_FOUND,
                        "portonePaymentId=" + portonePaymentId
                ));

        // 1. Order + items 먼저 로드 (FETCH JOIN, cancel 전에 items 확보)
        Order order = orderService.findOrderEntity(payment.getOrderId());

        // 2. Payment 상태 전이
        payment.markFailed(reason);

        // 3. 재고 복구 — productId 정렬(데드락 방지) → 비관락 단위 호출
        order.getOrderItems().stream()
                .sorted(Comparator.comparing(OrderItem::getProductId))
                .forEach(oi -> productService.restoreStockWithLock(
                        oi.getProductId(), oi.getQuantity()));

        // 4. Order 상태 전이
        orderService.cancelOrder(payment.getOrderId());
    }
}
