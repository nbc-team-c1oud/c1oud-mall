package nbc.c1oud_mall.product.application.dto;

import lombok.Builder;
import lombok.Getter;
import nbc.c1oud_mall.product.domain.ProductStatus;

@Getter
public class ProductSearchCondition {

    private final String category;
    private final Long minPrice;
    private final Long maxPrice;
    private final ProductStatus status;

    @Builder
    private ProductSearchCondition(String category, Long minPrice, Long maxPrice, ProductStatus status) {
        this.category = category;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.status = status;
    }
}
