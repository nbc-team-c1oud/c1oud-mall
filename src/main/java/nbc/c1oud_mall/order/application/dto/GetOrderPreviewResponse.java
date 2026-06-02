package nbc.c1oud_mall.order.application.dto;

import lombok.Getter;

import java.util.List;

@Getter
public class GetOrderPreviewResponse {

    //장바구니에 있는거를 만드는거임.
    private final List<GetOrderItemPreviewResponse> items;
    private final Long totalPrice;

    public GetOrderPreviewResponse(List<GetOrderItemPreviewResponse> items, Long totalPrice) {
        this.items = items;
        this.totalPrice = totalPrice;
    }
}
