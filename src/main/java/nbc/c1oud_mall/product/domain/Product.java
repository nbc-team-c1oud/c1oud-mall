package nbc.c1oud_mall.product.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import nbc.c1oud_mall.common.domain.BaseEntity;
import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.common.exception.ErrorCode;

@Entity
@Table
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseEntity {

    @Id
    @GeneratedValue(strategy =  GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_name", nullable = false)
    private String name;

    @Column(nullable = false)
    private Long price;

    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity;

    @Column(nullable = false)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_status", nullable = false)
    private ProductStatus status;

    @Column(name = "product_description", columnDefinition = "TEXT")
    private String description;

    @Builder
    public Product(String name, Long price, Integer stockQuantity, String category, ProductStatus status, String description) {
        if (price < 0) {
            throw new BusinessException(ErrorCode.INVALID_PRICE);
        }

        if (stockQuantity < 0) {
            throw new BusinessException(ErrorCode.INVALID_STOCK);
        }

        this.name = name;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.category = category;
        this.status = status;
        this.description = description;
    }

    //재고 유효성 검증 및 차감
    public void deductStock(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ErrorCode.INVALID_QUANTITY);
        }
        if (quantity > this.stockQuantity) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
        }
        this.stockQuantity -= quantity;
    }
}
