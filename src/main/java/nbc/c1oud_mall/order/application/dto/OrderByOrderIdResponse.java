package nbc.c1oud_mall.order.application.dto;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class OrderByOrderIdResponse {

    private final Long orderId;
    private final Long paymentId;
    private final String orderNumber;
    private final String orderStatus;
    private final Long totalAmount;
    private final LocalDateTime createAt;
    private final List<OrderItemResponse> orderItems;

    public OrderByOrderIdResponse(Long orderId, Long paymentId, String orderNumber, String orderStatus, Long totalAmount, LocalDateTime createAt, List<OrderItemResponse> orderItems) {
        this.orderId = orderId;
        this.paymentId = paymentId;
        this.orderNumber = orderNumber;
        this.orderStatus = orderStatus;
        this.totalAmount = totalAmount;
        this.createAt = createAt;
        this.orderItems = orderItems;
    }


}
