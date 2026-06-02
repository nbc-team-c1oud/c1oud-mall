package nbc.c1oud_mall.order.application.dto;

import lombok.Getter;

@Getter
public class OrderCheckoutResponse {

    private final Long orderId;
    private final String portonePaymentId;
    private final String orderNumber;
    private final String orderName;
    private final String orderstatus;
    private final Long totalAmount;

    public OrderCheckoutResponse(Long orderId, String portonePaymentId, String orderNumber, String orderName, String orderstatus, Long totalAmount) {
        this.orderId = orderId;
        this.portonePaymentId = portonePaymentId;
        this.orderNumber = orderNumber;
        this.orderName = orderName;
        this.orderstatus = orderstatus;
        this.totalAmount = totalAmount;
    }
}
