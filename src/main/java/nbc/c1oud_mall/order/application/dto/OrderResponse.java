package nbc.c1oud_mall.order.application.dto;

import lombok.Getter;
import nbc.c1oud_mall.payment.domain.PaymentStatus;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class OrderResponse {

    private final Long orderId;
    private final Long paymentId;
    private final PaymentStatus paymentStatus;
    private final String orderNumber;
    private final String orderStatus;
    private final Long totalAmount;
    private final Long pgAmount;
    private final Long pointUsedAmount;
    private final Long pointEarnedAmount;
    private final LocalDateTime createAt;
    private final List<OrderItemResponse> orderItems;

    public OrderResponse(
            Long orderId,
            Long paymentId,
            PaymentStatus paymentStatus,
            String orderNumber,
            String orderStatus,
            Long totalAmount,
            Long pgAmount,
            Long pointUsedAmount,
            Long pointEarnedAmount,
            LocalDateTime createAt,
            List<OrderItemResponse> orderItems
    ) {
        this.orderId = orderId;
        this.paymentId = paymentId;
        this.paymentStatus = paymentStatus;
        this.orderNumber = orderNumber;
        this.orderStatus = orderStatus;
        this.totalAmount = totalAmount;
        this.pgAmount = pgAmount;
        this.pointUsedAmount = pointUsedAmount;
        this.pointEarnedAmount = pointEarnedAmount;
        this.createAt = createAt;
        this.orderItems = orderItems;
    }
}