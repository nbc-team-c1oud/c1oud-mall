package nbc.c1oud_mall.cart.application.dto;

import jakarta.validation.constraints.Min;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItemUpdateRequest {

    @Min(value = 1, message = "변경할 수량은 최소 1개 이상이어야 합니다.")
    private int quantity;

    // 테스트 코드 등을 위한 생성자
    public CartItemUpdateRequest(int quantity) {
        this.quantity = quantity;
    }
}