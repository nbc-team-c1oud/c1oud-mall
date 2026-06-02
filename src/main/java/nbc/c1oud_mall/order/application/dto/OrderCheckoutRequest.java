package nbc.c1oud_mall.order.application.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class OrderCheckoutRequest {

    private List<Long> cartItemIds = List.of();

    public OrderCheckoutRequest(List<Long> cartItems) {
        this.cartItemIds = cartItems != null ? cartItemIds : List.of();
    }
}
