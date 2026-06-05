package nbc.c1oud_mall.cart.application.dto;

import lombok.Getter;
import nbc.c1oud_mall.cart.domain.CartItem;

@Getter
public class CartItemResponse {

    private final Long cartItemId;
    private final Long productId;
    private final String productName;
    private final Long price;
    private final int quantity;
    private final Long subTotal;

    public CartItemResponse(CartItem cartItem) {
        this.cartItemId = cartItem.getId();
        this.productId = cartItem.getProduct().getId();
        this.productName = cartItem.getProduct().getName();
        this.price = cartItem.getProduct().getPrice();
        this.quantity = cartItem.getQuantity();
        this.subTotal = this.price * this.quantity;
    }
}
