package nbc.c1oud_mall.payment.application;

import nbc.c1oud_mall.auth.domain.entity.User;
import nbc.c1oud_mall.auth.infrastructure.UserRepository;
import nbc.c1oud_mall.cart.domain.CartItem;
import nbc.c1oud_mall.cart.infrastructure.CartItemJpaRepository;
import nbc.c1oud_mall.order.domain.Order;
import nbc.c1oud_mall.order.domain.OrderItem;
import nbc.c1oud_mall.order.domain.OrderStatus;
import nbc.c1oud_mall.order.infrastructure.OrderJpaRepository;
import nbc.c1oud_mall.payment.application.dto.PaymentConfirmationResult;
import nbc.c1oud_mall.payment.application.dto.PortOnePaymentInfo;
import nbc.c1oud_mall.payment.application.dto.PortOnePaymentStatus;
import nbc.c1oud_mall.payment.application.dto.command.PaymentConfirmationCommand;
import nbc.c1oud_mall.payment.domain.Payment;
import nbc.c1oud_mall.payment.domain.PaymentStatus;
import nbc.c1oud_mall.payment.infrastructure.PaymentJpaRepository;
import nbc.c1oud_mall.point.infrastructure.PointJpaRepository;
import nbc.c1oud_mall.product.domain.Product;
import nbc.c1oud_mall.product.domain.ProductStatus;
import nbc.c1oud_mall.product.infrastructure.ProductJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * 회귀 차단: CartItemJpaRepository.deleteAllByUserId의 clearAutomatically=true가 같은 TX
 * 내 Payment/Order의 dirty 변경분을 flush 전에 detach시키던 사일런트 데이터 손실 버그.
 *
 * 본 테스트는 실 영속성 컨텍스트(@SpringBootTest)로 confirm을 호출한 뒤 DB에 직접 재조회하여
 * payment.status == COMPLETED, order.status == CONFIRMED가 영속됐는지를 확인한다. 수정 전에는
 * 응답은 성공이지만 DB는 PENDING/PENDING_PAYMENT인 채로 남음.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PaymentConfirmationServiceCartClearIntegrationTest {

    @Autowired
    private PaymentConfirmationService paymentConfirmationService;

    @Autowired
    private PaymentJpaRepository paymentRepository;
    @Autowired
    private OrderJpaRepository orderJpaRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProductJpaRepository productJpaRepository;
    @Autowired
    private CartItemJpaRepository cartItemJpaRepository;
    @Autowired
    private PointJpaRepository pointJpaRepository;

    @MockitoBean
    private PortOnePaymentQueryPort portOnePaymentQueryPort;
    @MockitoBean
    private PortOnePaymentCancelPort portOnePaymentCancelPort;

    @AfterEach
    void cleanup() {
        pointJpaRepository.deleteAll();   // PointHistory가 Payment·Order FK 보유 → 먼저
        cartItemJpaRepository.deleteAll();
        paymentRepository.deleteAll();
        orderJpaRepository.deleteAll();
        productJpaRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("confirm 정상 흐름 후 DB 영속 검증: Payment.COMPLETED + Order.CONFIRMED + cart 비움")
    void confirm_persists_completed_states_and_clears_cart() {
        // 픽스처: User + Product + Order(PENDING_PAYMENT) + Payment(PENDING) + CartItem 1건
        User user = userRepository.saveAndFlush(
                new User("cart-clear-test@test.com", "pw", "테스터", "010-0000-2222"));

        Product product = productJpaRepository.saveAndFlush(Product.builder()
                .name("테스트상품")
                .price(5_000L)
                .stockQuantity(10)
                .category("ELECTRONICS")
                .status(ProductStatus.SALE)
                .description("confirm 통합테스트용")
                .build());

        OrderItem item = new OrderItem(product, "테스트상품", 5_000L, 1);
        Order order = orderJpaRepository.saveAndFlush(Order.builder()
                .user(user)
                .totalAmount(5_000L)
                .orderItems(List.of(item))
                .build());

        Payment payment = paymentRepository.saveAndFlush(
                Payment.of(order.getId(), user.getId(), 5_000L, 5_000L, 0L));
        String portoneId = payment.getPortonePaymentId();

        cartItemJpaRepository.saveAndFlush(CartItem.builder()
                .userId(user.getId())
                .product(product)
                .quantity(1)
                .build());

        PortOnePaymentInfo info = new PortOnePaymentInfo(
                portoneId, PortOnePaymentStatus.PAID, 5_000L, "TOSSPAYMENTS", "pg-tx-cart-clear-001");
        given(portOnePaymentQueryPort.query(portoneId)).willReturn(info);

        // when
        PaymentConfirmationResult result = paymentConfirmationService.confirm(
                new PaymentConfirmationCommand(portoneId, user.getId(), order.getId()));

        // then: 응답이 confirmed임은 기본 (수정 전에도 응답은 성공이었음)
        assertThat(result.alreadyCompleted()).isFalse();
        assertThat(result.status()).isEqualTo(PaymentStatus.COMPLETED);

        // 핵심: DB에 실제로 UPDATE가 반영됐는지 (수정 전엔 둘 다 그대로 PENDING/PENDING_PAYMENT)
        Payment paymentFromDb = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(paymentFromDb.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(paymentFromDb.getPgTxId()).isEqualTo("pg-tx-cart-clear-001");
        assertThat(paymentFromDb.getConfirmedAt()).isNotNull();

        Order orderFromDb = orderJpaRepository.findById(order.getId()).orElseThrow();
        assertThat(orderFromDb.getOrderStatus()).isEqualTo(OrderStatus.CONFIRMED);

        // cart 비움도 함께 검증
        assertThat(cartItemJpaRepository.findByUserId(user.getId())).isEmpty();

        // 적립 포인트 검증: totalAmount 5000 × 1% = 50p 적립
        User userFromDb = userRepository.findById(user.getId()).orElseThrow();
        assertThat(userFromDb.getPointBalance()).isEqualTo(50L);
        assertThat(paymentFromDb.getPointEarnedAmount()).isEqualTo(50L);
    }
}
