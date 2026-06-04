package nbc.c1oud_mall.order.application.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class OrderCheckoutRequest {

    private List<Long> cartItemIds = List.of();
    private Long pointUsedAmount = 0L;

    public OrderCheckoutRequest(List<Long> cartItemIds, Long pointUsedAmount) {
        this.cartItemIds = cartItemIds != null ? cartItemIds : List.of();
        this.pointUsedAmount = pointUsedAmount != null ? pointUsedAmount : 0L;
    }
}
