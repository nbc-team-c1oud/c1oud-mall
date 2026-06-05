package nbc.c1oud_mall.cart.application.dto;

import lombok.Getter;

import java.util.List;

@Getter
public class CartListResponse {

    private final List<CartItemResponse> items;
    private final Long totalPrice;

    public CartListResponse(List<CartItemResponse> items) {
        this.items = items;
        this.totalPrice = items.stream()
                .mapToLong(CartItemResponse::getSubTotal)
                .sum();
    }
}
