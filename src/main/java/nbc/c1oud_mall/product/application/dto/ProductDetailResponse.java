package nbc.c1oud_mall.product.application.dto;

import lombok.Builder;
import lombok.Getter;
import nbc.c1oud_mall.product.domain.Product;
import nbc.c1oud_mall.product.domain.ProductStatus;

@Getter
public class ProductDetailResponse {

    private final Long id;
    private final String name;
    private final Long price;
    private final Integer stockQuantity;
    private final String category;
    private final ProductStatus status;
    private final String description;

    @Builder
    private ProductDetailResponse(Long id, String name, Long price, Integer stockQuantity, String category, ProductStatus status, String description) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.category = category;
        this.status = status;
        this.description = description;
    }

    public static ProductDetailResponse from(Product product) {
        return ProductDetailResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .price(product.getPrice())
                .stockQuantity(product.getStockQuantity())
                .category(product.getCategory())
                .status(product.getStatus())
                .description(product.getDescription())
                .build();
    }
}
