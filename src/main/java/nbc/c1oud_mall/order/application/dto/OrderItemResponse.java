package nbc.c1oud_mall.order.application.dto;

import lombok.Getter;
import nbc.c1oud_mall.order.domain.OrderItem;

@Getter
public class OrderItemResponse {

    private final String productNameSnapshot;

    private final Long priceSnapshot;

    private final Integer quantity;

    public OrderItemResponse(String productNameSnapshot, Long priceSnapshot, Integer quantity) {
        this.productNameSnapshot = productNameSnapshot;
        this.priceSnapshot = priceSnapshot;
        this.quantity = quantity;
    }

    public static OrderItemResponse from(OrderItem orderItem) {
        return new OrderItemResponse(
                orderItem.getProductNameSnapshot(),
                orderItem.getPriceSnapshot(),
                orderItem.getQuantity()
        );
    }
}
