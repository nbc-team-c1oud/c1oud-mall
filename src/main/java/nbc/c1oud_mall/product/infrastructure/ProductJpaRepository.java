package nbc.c1oud_mall.product.infrastructure;

import nbc.c1oud_mall.product.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductJpaRepository extends JpaRepository<Product, Long>, ProductJpaRepositoryCustom {
}
