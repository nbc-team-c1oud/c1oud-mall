package nbc.c1oud_mall.common.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nbc.c1oud_mall.product.domain.Product;
import nbc.c1oud_mall.product.domain.ProductStatus;
import nbc.c1oud_mall.product.infrastructure.ProductJpaRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;

@Slf4j
@Configuration
@Profile("dev")
@RequiredArgsConstructor
public class DummyDataInit {

    private final ProductJpaRepository productJpaRepository;

    @Bean
    public CommandLineRunner initProductDummyData() {
        return args -> {
            if (productJpaRepository.count() > 0) {
                return;
            }

            List<Product> dummyProducts = List.of(
                    Product.builder()
                            .name("맛있는 고흥 붉바리 (생물)")
                            .price(45000L)
                            .stockQuantity(12)
                            .category("수산물")
                            .status(ProductStatus.SALE)
                            .description("고흥 앞바다에서 갓 잡은 싱싱한 최고급 붉바리입니다.")
                            .build(),
                    Product.builder()
                            .name("거제도 볼락 루어 바늘")
                            .price(5000L)
                            .stockQuantity(100)
                            .category("낚시용품")
                            .status(ProductStatus.SALE)
                            .description("볼락 루어 낚시에 최적화된 바늘입니다. (10개입)")
                            .build(),
                    Product.builder()
                            .name("동해안 반건조 오징어 10미")
                            .price(28000L)
                            .stockQuantity(50)
                            .category("수산물")
                            .status(ProductStatus.SALE)
                            .description("해풍에 말려 더욱 쫄깃하고 맛있는 구룡포 반건조 오징어입니다.")
                            .build()
            );

            productJpaRepository.saveAll(dummyProducts);
            log.info("[DummyDataInit] 상품 더미 데이터 {}건 삽입 완료.", dummyProducts.size());
        };
    }
}