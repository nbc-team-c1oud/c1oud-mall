package nbc.c1oud_mall.cart.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.common.exception.ErrorCode;
import nbc.c1oud_mall.product.domain.Product;

@Entity
@Table(name = "cart_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    @Builder
    public CartItem(Long userId, Product product, int quantity) {
        this.userId = userId;
        this.product = product;
        this.quantity = quantity;
    }

    // 기존 상품에 수량 합산
    public void addQuantity(int amount) {
        validateStock(this.quantity + amount);
        this.quantity += amount;
    }

    // 수량 변경
    public void updateQuantity(int quantity) {
        validateStock(quantity);
        this.quantity = quantity;
    }

    // 재고 초과 검증
    public void validateStock(int targetQuantity) {
        if (this.product.getStockQuantity() < targetQuantity) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
        }
    }

    // 소유자 확인
    public void validateOwner(Long requestUserId) {
        if (!this.userId.equals(requestUserId)) {
            throw new BusinessException(ErrorCode.CART_ACCESS_DENIED);
        }
    }
}
