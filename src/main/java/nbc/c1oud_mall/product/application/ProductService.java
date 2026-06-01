package nbc.c1oud_mall.product.application;

import lombok.RequiredArgsConstructor;
import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.common.exception.ErrorCode;
import nbc.c1oud_mall.product.application.dto.ProductResponse;
import nbc.c1oud_mall.product.domain.Product;
import nbc.c1oud_mall.product.infrastructure.ProductJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor

public class ProductService {

    private final ProductJpaRepository productJpaRepository;

    @Transactional(readOnly = true)
    public ProductResponse getProduct(Long productId) {
        Product product = productJpaRepository.findById(productId).orElseThrow(
                () -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND)
        );

        return ProductResponse.from(product);
    }
}
