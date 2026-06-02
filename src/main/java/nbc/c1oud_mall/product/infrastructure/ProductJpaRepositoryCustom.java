package nbc.c1oud_mall.product.infrastructure;

import nbc.c1oud_mall.product.application.dto.ProductSearchCondition;
import nbc.c1oud_mall.product.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductJpaRepositoryCustom {
    Page<Product> findAllByCondition(ProductSearchCondition condition, Pageable pageable);
}
