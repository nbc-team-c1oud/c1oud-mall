package nbc.c1oud_mall.product.application;

import lombok.RequiredArgsConstructor;
import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.common.exception.ErrorCode;
import nbc.c1oud_mall.product.application.dto.ProductDetailResponse;
import nbc.c1oud_mall.product.application.dto.ProductListResponse;
import nbc.c1oud_mall.product.application.dto.ProductSearchCondition;
import nbc.c1oud_mall.product.domain.Product;
import nbc.c1oud_mall.product.infrastructure.ProductJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor

public class ProductService {

    private final ProductJpaRepository productJpaRepository;

    @Transactional(readOnly = true)
    public ProductDetailResponse getProduct(Long productId) {
        Product product = productJpaRepository.findById(productId).orElseThrow(
                () -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND)
        );

        return ProductDetailResponse.from(product);
    }

    @Transactional(readOnly = true)
    public Page<ProductListResponse> getProducts(ProductSearchCondition condition, Pageable pageable){
        Page<Product> products = productJpaRepository.findAllByCondition(condition, pageable);
        return products.map(ProductListResponse::from);
    }
}
