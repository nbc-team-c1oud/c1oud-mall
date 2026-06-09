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
@Profile("prod")
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
                    // ── 수산물 (8) ──
                    Product.builder()
                            .name("맛있는 고흥 붉바리 (생물)")
                            .price(45_000L)
                            .stockQuantity(12)
                            .category("수산물")
                            .status(ProductStatus.SALE)
                            .description("고흥 앞바다에서 갓 잡은 싱싱한 최고급 붉바리입니다.")
                            .build(),
                    Product.builder()
                            .name("동해안 반건조 오징어 10미")
                            .price(28_000L)
                            .stockQuantity(50)
                            .category("수산물")
                            .status(ProductStatus.SALE)
                            .description("해풍에 말려 더욱 쫄깃하고 맛있는 구룡포 반건조 오징어입니다.")
                            .build(),
                    Product.builder()
                            .name("완도 활전복 1kg (8미 내외)")
                            .price(65_000L)
                            .stockQuantity(20)
                            .category("수산물")
                            .status(ProductStatus.SALE)
                            .description("완도 청정해역에서 자란 활전복. 산지 직송으로 신선도 최상.")
                            .build(),
                    Product.builder()
                            .name("통영 굴 생굴 5kg")
                            .price(35_000L)
                            .stockQuantity(30)
                            .category("수산물")
                            .status(ProductStatus.SALE)
                            .description("탱글탱글한 통영 굴. 굴전·굴국밥·생굴초장 모두 좋습니다.")
                            .build(),
                    Product.builder()
                            .name("제주 흑돔 활어회 모듬 (2~3인)")
                            .price(89_000L)
                            .stockQuantity(8)
                            .category("수산물")
                            .status(ProductStatus.SALE)
                            .description("제주산 자연산 흑돔 활회. 채소·소스·매운탕 거리까지 풀패키지.")
                            .build(),
                    Product.builder()
                            .name("부산 자갈치 활낙지 5미")
                            .price(32_000L)
                            .stockQuantity(15)
                            .category("수산물")
                            .status(ProductStatus.SALE)
                            .description("새벽 자갈치 시장 직송. 산낙지·연포탕·낙지볶음용.")
                            .build(),
                    Product.builder()
                            .name("영광 굴비 1두름 (10미, 굵은 사이즈)")
                            .price(75_000L)
                            .stockQuantity(25)
                            .category("수산물")
                            .status(ProductStatus.SALE)
                            .description("전남 영광 법성포 천일염 굴비. 명절·선물용으로 적합.")
                            .build(),
                    Product.builder()
                            .name("서산 활새조개 1kg")
                            .price(42_000L)
                            .stockQuantity(18)
                            .category("수산물")
                            .status(ProductStatus.SALE)
                            .description("서산 앞바다 자연산. 샤브샤브·구이로 단맛이 일품.")
                            .build(),

                    // ── 낚시용품 (5) ──
                    Product.builder()
                            .name("거제도 볼락 루어 바늘 (10개입)")
                            .price(5_000L)
                            .stockQuantity(100)
                            .category("낚시용품")
                            .status(ProductStatus.SALE)
                            .description("볼락 루어 낚시에 최적화된 바늘. 무게·후크 셋업 검증 완료.")
                            .build(),
                    Product.builder()
                            .name("시마노 스피닝 릴 STELLA SW8000HG")
                            .price(850_000L)
                            .stockQuantity(5)
                            .category("낚시용품")
                            .status(ProductStatus.SALE)
                            .description("시마노 플래그십 스피닝 릴. 대물 지깅·캐스팅에 최적.")
                            .build(),
                    Product.builder()
                            .name("다이와 카본 갯바위 낚싯대 4.5m")
                            .price(280_000L)
                            .stockQuantity(10)
                            .category("낚시용품")
                            .status(ProductStatus.SALE)
                            .description("초경량 카본 소재. 감도와 강도 모두 균형 잡힌 갯바위 전용.")
                            .build(),
                    Product.builder()
                            .name("갈치 전용 인터라인 라인 300m")
                            .price(15_000L)
                            .stockQuantity(60)
                            .category("낚시용품")
                            .status(ProductStatus.SALE)
                            .description("갈치 채낚기 전용. 야간 시인성 좋은 형광 코팅.")
                            .build(),
                    Product.builder()
                            .name("야광 우럭 미끼 세트 (5종)")
                            .price(12_000L)
                            .stockQuantity(80)
                            .category("낚시용품")
                            .status(ProductStatus.SALE)
                            .description("우럭·광어용 야광 웜·지그 5종 컴플리트 세트.")
                            .build(),

                    // ── 가공식품 (5) ──
                    Product.builder()
                            .name("진라면 매운맛 (5봉지)")
                            .price(4_500L)
                            .stockQuantity(500)
                            .category("가공식품")
                            .status(ProductStatus.SALE)
                            .description("진하고 깊은 국물의 진라면 매운맛 5봉 묶음.")
                            .build(),
                    Product.builder()
                            .name("동원 참치 살코기 100g × 12캔")
                            .price(18_900L)
                            .stockQuantity(200)
                            .category("가공식품")
                            .status(ProductStatus.SALE)
                            .description("국민 참치 동원 살코기 100g 12캔 박스. 김밥·찌개·샐러드용.")
                            .build(),
                    Product.builder()
                            .name("청정원 양조간장 1.8L")
                            .price(7_800L)
                            .stockQuantity(150)
                            .category("가공식품")
                            .status(ProductStatus.SALE)
                            .description("180일 자연 숙성 양조간장. 깊은 감칠맛.")
                            .build(),
                    Product.builder()
                            .name("오뚜기 카레 매운맛 100g × 5개")
                            .price(5_500L)
                            .stockQuantity(180)
                            .category("가공식품")
                            .status(ProductStatus.SALE)
                            .description("뜨거운 물에 풀어 5분이면 완성. 든든한 한끼.")
                            .build(),
                    Product.builder()
                            .name("비비고 왕교자 만두 1.2kg")
                            .price(12_000L)
                            .stockQuantity(120)
                            .category("가공식품")
                            .status(ProductStatus.SALE)
                            .description("비비고 왕교자 1.2kg 대용량팩. 군만두·찐만두·만두국 모두.")
                            .build(),

                    // ── 농산물 (5) ──
                    Product.builder()
                            .name("충주 사과 5kg (씨알 굵음)")
                            .price(28_000L)
                            .stockQuantity(40)
                            .category("농산물")
                            .status(ProductStatus.SALE)
                            .description("충주 일교차로 단단하고 단맛 진한 사과. 가정용 5kg.")
                            .build(),
                    Product.builder()
                            .name("제주 한라봉 5kg")
                            .price(35_000L)
                            .stockQuantity(35)
                            .category("농산물")
                            .status(ProductStatus.SALE)
                            .description("당도 13브릭스 이상 제주 한라봉. 명절 선물용으로도 좋습니다.")
                            .build(),
                    Product.builder()
                            .name("청송 사과즙 100ml × 50봉")
                            .price(22_000L)
                            .stockQuantity(70)
                            .category("농산물")
                            .status(ProductStatus.SALE)
                            .description("무첨가 100% 청송 사과즙. 어린이 간식·아침 한잔.")
                            .build(),
                    Product.builder()
                            .name("해남 단호박 3통")
                            .price(15_000L)
                            .stockQuantity(45)
                            .category("농산물")
                            .status(ProductStatus.SALE)
                            .description("해남 황토에서 자란 단호박. 죽·찜·구이용.")
                            .build(),
                    Product.builder()
                            .name("강원도 햇감자 10kg")
                            .price(18_000L)
                            .stockQuantity(55)
                            .category("농산물")
                            .status(ProductStatus.SALE)
                            .description("강원 고랭지에서 갓 수확한 햇감자. 포슬포슬한 식감.")
                            .build(),

                    // ── 정육 (3) ──
                    Product.builder()
                            .name("한우 1++ 등심 500g")
                            .price(95_000L)
                            .stockQuantity(15)
                            .category("정육")
                            .status(ProductStatus.SALE)
                            .description("최고 등급 1++ 한우 등심. 진공포장 산지 직송.")
                            .build(),
                    Product.builder()
                            .name("제주 흑돼지 삼겹살 1kg")
                            .price(38_000L)
                            .stockQuantity(30)
                            .category("정육")
                            .status(ProductStatus.SALE)
                            .description("제주 흑돼지 1kg 진공포장. 쫀득한 식감과 깊은 풍미.")
                            .build(),
                    Product.builder()
                            .name("국내산 닭다리살 2kg (냉장)")
                            .price(16_800L)
                            .stockQuantity(50)
                            .category("정육")
                            .status(ProductStatus.SALE)
                            .description("부드러운 닭다리살 2kg 박스. 닭갈비·치킨·국용으로 다용도.")
                            .build(),

                    // ── 주방·잡화 (4) ──
                    Product.builder()
                            .name("락앤락 밀폐용기 12종 세트")
                            .price(32_000L)
                            .stockQuantity(60)
                            .category("주방용품")
                            .status(ProductStatus.SALE)
                            .description("내열·내한성 우수한 락앤락 인기 12종 종합 세트.")
                            .build(),
                    Product.builder()
                            .name("휘슬러 압력솥 6L")
                            .price(240_000L)
                            .stockQuantity(8)
                            .category("주방용품")
                            .status(ProductStatus.SALE)
                            .description("독일 휘슬러 정품 6L 압력솥. 평생 사용 가능한 명품.")
                            .build(),
                    Product.builder()
                            .name("무인양품 스테인리스 수저 4인 세트")
                            .price(28_000L)
                            .stockQuantity(40)
                            .category("주방용품")
                            .status(ProductStatus.SALE)
                            .description("무광 마감 스테인리스 수저. 4인 가족용 세트.")
                            .build(),
                    Product.builder()
                            .name("코맥스 인덕션 후라이팬 28cm")
                            .price(35_000L)
                            .stockQuantity(35)
                            .category("주방용품")
                            .status(ProductStatus.SALE)
                            .description("인덕션·가스 겸용. 다이아몬드 코팅으로 눌어붙지 않음.")
                            .build(),

                    // ── 결제 e2e 테스트 (2) — 운영 노출 OK, 라벨로 구분 ──
                    Product.builder()
                            .name("[테스트] 1원 결제 검증용 상품")
                            .price(1L)
                            .stockQuantity(9999)
                            .category("테스트")
                            .status(ProductStatus.SALE)
                            .description("PortOne 결제 e2e 테스트용. 운영 검증 완료 후 비공개 전환 예정.")
                            .build(),
                    Product.builder()
                            .name("[테스트] 1000원 결제 검증용 상품")
                            .price(1_000L)
                            .stockQuantity(9999)
                            .category("테스트")
                            .status(ProductStatus.SALE)
                            .description("KG이니시스 최소 결제 금액(1000원) e2e 테스트용. 운영 검증 완료 후 비공개 전환 예정.")
                            .build()
            );

            productJpaRepository.saveAll(dummyProducts);
            log.info("[DummyDataInit] 상품 더미 데이터 {}건 삽입 완료.", dummyProducts.size());
        };
    }
}
