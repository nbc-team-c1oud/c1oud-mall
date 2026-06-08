package nbc.c1oud_mall.payment.application;

import nbc.c1oud_mall.auth.domain.UserRole;
import nbc.c1oud_mall.auth.domain.entity.User;
import nbc.c1oud_mall.auth.infrastructure.UserRepository;
import nbc.c1oud_mall.cart.infrastructure.CartItemJpaRepository;
import nbc.c1oud_mall.order.domain.Order;
import nbc.c1oud_mall.order.domain.OrderItem;
import nbc.c1oud_mall.order.infrastructure.OrderJpaRepository;
import nbc.c1oud_mall.payment.application.dto.PaymentConfirmationResult;
import nbc.c1oud_mall.payment.application.dto.PortOnePaymentInfo;
import nbc.c1oud_mall.payment.application.dto.PortOnePaymentStatus;
import nbc.c1oud_mall.payment.application.dto.command.PaymentConfirmationCommand;
import nbc.c1oud_mall.payment.domain.Payment;
import nbc.c1oud_mall.payment.domain.PaymentStatus;
import nbc.c1oud_mall.payment.infrastructure.PaymentJpaRepository;
import nbc.c1oud_mall.point.domain.PointHistory;
import nbc.c1oud_mall.point.domain.PointTransactionType;
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
 * 결제 확정 시 적립 포인트가 (1) Payment.pointEarnedAmount (2) User.pointBalance
 * (3) PointHistory(EARN) 행으로 모두 영속되는지 end-to-end 검증.
 *
 * <p>회귀 차단:
 * <ul>
 *   <li>PointPolicy 미적용 → (1)(2)(3) 0 / 미저장 → 본 PR의 적립 도입으로 해소</li>
 *   <li>CartItemJpaRepository.deleteAllByUserId의 flushAutomatically 누락 →
 *       cart bulk DELETE 직전 PC clear로 dirty User entity detach → (2)(3) 실패 →
 *       본 PR의 flushAutomatically=true 추가로 해소</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PaymentConfirmationPointAccrualIntegrationTest {

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
        // FK 순서: PointHistory → Payment → Order → Product → User
        pointJpaRepository.deleteAll();
        cartItemJpaRepository.deleteAll();
        paymentRepository.deleteAll();
        orderJpaRepository.deleteAll();
        productJpaRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("confirm 후 Payment.pointEarnedAmount + User.pointBalance + PointHistory(EARN) 모두 영속")
    void confirm_persists_earned_points_to_user_history_and_payment() {
        // 픽스처: pointBalance=0 User + Order(PENDING_PAYMENT) + Payment(PENDING, total 5,000)
        User user = userRepository.saveAndFlush(
                new User("point-accrual@test.com", "pw", "적립테스터", "010-0000-3333", UserRole.USER));

        Product product = productJpaRepository.saveAndFlush(Product.builder()
                .name("테스트상품")
                .price(5_000L)
                .stockQuantity(10)
                .category("ELECTRONICS")
                .status(ProductStatus.SALE)
                .description("적립 통합테스트용")
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

        PortOnePaymentInfo info = new PortOnePaymentInfo(
                portoneId, PortOnePaymentStatus.PAID, 5_000L, "TOSSPAYMENTS", "pg-tx-accrual-001");
        given(portOnePaymentQueryPort.query(portoneId)).willReturn(info);

        // when
        PaymentConfirmationResult result = paymentConfirmationService.confirm(
                new PaymentConfirmationCommand(portoneId, user.getId(), order.getId()));

        // then: 응답
        assertThat(result.alreadyCompleted()).isFalse();
        assertThat(result.status()).isEqualTo(PaymentStatus.COMPLETED);

        // (1) Payment.pointEarnedAmount = 5,000 × 1% = 50
        Payment paymentFromDb = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(paymentFromDb.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(paymentFromDb.getPointEarnedAmount()).isEqualTo(50L);

        // (2) User.pointBalance: 0 → 50
        User userFromDb = userRepository.findById(user.getId()).orElseThrow();
        assertThat(userFromDb.getPointBalance()).isEqualTo(50L);

        // (3) PointHistory(EARN, amount=50, balanceAfter=50) 1행
        List<PointHistory> histories = pointJpaRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        assertThat(histories).hasSize(1);
        PointHistory earnHistory = histories.get(0);
        assertThat(earnHistory.getTransactionType()).isEqualTo(PointTransactionType.EARN);
        assertThat(earnHistory.getAmount()).isEqualTo(50L);
        assertThat(earnHistory.getBalanceAfter()).isEqualTo(50L);
    }
}
