package nbc.c1oud_mall.order.application.dto;

import lombok.Getter;

@Getter
public class GetOrderItemPreviewResponse {

    private final Long productId;
    private final String productName;
    private final Long price;
    private final Integer quantity;
    private final Long subtotal;

    public GetOrderItemPreviewResponse(Long productId, String productName, Long price, Integer quantity, Long subtotal) {
        this.productId = productId;
        this.productName = productName;
        this.price = price;
        this.quantity = quantity;
        this.subtotal = subtotal;
    }
}
