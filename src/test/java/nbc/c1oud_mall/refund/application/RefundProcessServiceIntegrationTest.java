package nbc.c1oud_mall.refund.application;

import nbc.c1oud_mall.auth.domain.UserRole;
import nbc.c1oud_mall.auth.domain.entity.User;
import nbc.c1oud_mall.auth.infrastructure.UserRepository;
import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.common.exception.ErrorCode;
import nbc.c1oud_mall.order.domain.Order;
import nbc.c1oud_mall.order.domain.OrderItem;
import nbc.c1oud_mall.order.infrastructure.OrderJpaRepository;
import nbc.c1oud_mall.payment.application.PortOnePaymentCancelPort;
import nbc.c1oud_mall.payment.domain.Payment;
import nbc.c1oud_mall.payment.infrastructure.PaymentJpaRepository;
import nbc.c1oud_mall.point.application.PointService;
import nbc.c1oud_mall.product.domain.Product;
import nbc.c1oud_mall.product.domain.ProductStatus;
import nbc.c1oud_mall.product.infrastructure.ProductJpaRepository;
import nbc.c1oud_mall.refund.application.dto.RefundResult;
import nbc.c1oud_mall.refund.application.dto.command.RefundCommand;
import nbc.c1oud_mall.refund.application.dto.command.RefundItemCommand;
import nbc.c1oud_mall.refund.domain.Refund;
import nbc.c1oud_mall.refund.domain.RefundStatus;
import nbc.c1oud_mall.refund.infrastructure.RefundJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RefundProcessServiceIntegrationTest {

    @Autowired
    private RefundProcessService refundProcessService;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProductJpaRepository productJpaRepository;
    @Autowired
    private OrderJpaRepository orderJpaRepository;
    @Autowired
    private PaymentJpaRepository paymentJpaRepository;
    @Autowired
    private RefundJpaRepository refundJpaRepository;

    @MockitoBean
    private PortOnePaymentCancelPort portOnePaymentCancelPort;
    @MockitoBean
    private PointService pointService;

    private Long savedUserId;
    private Long savedOrderId;
    private Long savedOrderItemId;
    private Long savedPaymentId;
    private Long savedProductId;
    private Integer initialStockQuantity;
    private String portonePaymentId;

    @BeforeEach
    void resetMocks() {
        Mockito.reset(portOnePaymentCancelPort, pointService);
    }

    @AfterEach
    void cleanup() {
        refundJpaRepository.deleteAll();
        paymentJpaRepository.deleteAll();
        orderJpaRepository.deleteAll();
        productJpaRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ── 픽스처 ──

    private void setupFixture(long pgAmount, long pointUsedAmount, int qty) {
        User user = userRepository.saveAndFlush(
                new User("refund-test@test.com", "pw", "환불테스터", "010-0000-1111", UserRole.USER));
        savedUserId = user.getId();

        initialStockQuantity = 10;
        Product product = productJpaRepository.saveAndFlush(Product.builder()
                .name("테스트상품")
                .price(5_000L)
                .stockQuantity(initialStockQuantity)
                .category("ELECTRONICS")
                .status(ProductStatus.SALE)
                .description("통합테스트용")
                .build());
        savedProductId = product.getId();

        OrderItem item = new OrderItem(product, "테스트상품", 5_000L, qty);
        Order order = Order.builder()
                .user(user)
                .totalAmount(pgAmount + pointUsedAmount)
                .orderItems(List.of(item))
                .build();
        Order savedOrder = orderJpaRepository.saveAndFlush(order);
        savedOrderId = savedOrder.getId();
        savedOrderItemId = savedOrder.getOrderItems().get(0).getId();

        Payment payment = Payment.of(savedOrderId, savedUserId,
                pgAmount + pointUsedAmount, pgAmount, pointUsedAmount);
        payment.markCompleted("pg-tx-int-001", 0L, LocalDateTime.now());
        Payment savedPayment = paymentJpaRepository.saveAndFlush(payment);
        savedPaymentId = savedPayment.getId();
        portonePaymentId = savedPayment.getPortonePaymentId();
    }

    // ── 테스트 ──

    @Test
    @DisplayName("정상 처리: DB에 Refund(PG_CANCELLED) 저장, cancel 1회 호출")
    void process_saves_pg_cancelled_refund_on_success() {
        setupFixture(9_000L, 0L, 2);

        RefundCommand command = new RefundCommand(savedOrderId, savedUserId,
                List.of(new RefundItemCommand(savedOrderItemId, 2)), "단순 변심");

        RefundResult result = refundProcessService.process(command);

        assertThat(result.finalStatus()).isEqualTo(RefundStatus.PG_CANCELLED);
        assertThat(result.refundId()).isNotNull();

        Refund fromDb = refundJpaRepository.findById(result.refundId()).orElseThrow();
        assertThat(fromDb.getStatus()).isEqualTo(RefundStatus.PG_CANCELLED);

        verify(portOnePaymentCancelPort).cancel(
                portonePaymentId, 10_000L, "단순 변심", "refund-" + result.refundId());

        // 재고 복구 검증: stockQuantity가 환불수량(2)만큼 증가
        Product productAfter = productJpaRepository.findById(savedProductId).orElseThrow();
        assertThat(productAfter.getStockQuantity()).isEqualTo(initialStockQuantity + 2);
    }

    @Test
    @DisplayName("PG 취소 실패: DB에 Refund(DB_COMMITTED) 유지, 예외 던지지 않음")
    void process_returns_db_committed_when_pg_cancel_fails() {
        setupFixture(9_000L, 0L, 2);
        willThrow(new BusinessException(ErrorCode.PORTONE_CANCEL_FAILED))
                .given(portOnePaymentCancelPort).cancel(any(), any(), any(), any());

        RefundCommand command = new RefundCommand(savedOrderId, savedUserId,
                List.of(new RefundItemCommand(savedOrderItemId, 2)), "PG실패테스트");

        RefundResult result = refundProcessService.process(command);

        assertThat(result.finalStatus()).isEqualTo(RefundStatus.DB_COMMITTED);

        Refund fromDb = refundJpaRepository.findById(result.refundId()).orElseThrow();
        assertThat(fromDb.getStatus()).isEqualTo(RefundStatus.DB_COMMITTED);
    }

    @Test
    @DisplayName("롤백: 포인트 복구 실패 시 Refund DB 미저장, cancel 미호출")
    void process_rolls_back_when_point_restore_throws() {
        setupFixture(8_000L, 2_000L, 2); // pointUsedAmount > 0 → pointService.restorePoints() 호출
        willThrow(new RuntimeException("포인트 서버 오류"))
                .given(pointService).restorePoints(anyLong(), anyLong(), any(Payment.class));

        RefundCommand command = new RefundCommand(savedOrderId, savedUserId,
                List.of(new RefundItemCommand(savedOrderItemId, 2)), "롤백테스트");

        assertThatThrownBy(() -> refundProcessService.process(command))
                .isInstanceOf(RuntimeException.class);

        assertThat(refundJpaRepository.count()).isZero();
        verify(portOnePaymentCancelPort, Mockito.never()).cancel(any(), any(), any(), any());

        // 롤백 검증: 포인트 복구가 재고 복구보다 먼저 실행되므로 stockQuantity는 변경된 적 없음
        Product productAfter = productJpaRepository.findById(savedProductId).orElseThrow();
        assertThat(productAfter.getStockQuantity()).isEqualTo(initialStockQuantity);
    }

    @Test
    @DisplayName("race: 동일 orderItem 동시 환불 - 1건 성공(DB_COMMITTED+), 1건 RF001 거부")
    void concurrent_refund_one_succeeds_one_rejected() throws Exception {
        setupFixture(9_000L, 0L, 2);

        RefundCommand command = new RefundCommand(savedOrderId, savedUserId,
                List.of(new RefundItemCommand(savedOrderItemId, 2)), "동시성 테스트");

        CountDownLatch start = new CountDownLatch(1);

        CompletableFuture<Object> f1 = CompletableFuture.supplyAsync(() -> {
            awaitLatch(start);
            try {
                return refundProcessService.process(command);
            } catch (BusinessException e) {
                return e;
            }
        });
        CompletableFuture<Object> f2 = CompletableFuture.supplyAsync(() -> {
            awaitLatch(start);
            try {
                return refundProcessService.process(command);
            } catch (BusinessException e) {
                return e;
            }
        });

        start.countDown();
        Object r1 = f1.get(10, TimeUnit.SECONDS);
        Object r2 = f2.get(10, TimeUnit.SECONDS);

        long successCount = countByType(r1, r2, RefundResult.class);
        long rfFailCount = countByErrorCode(r1, r2, ErrorCode.REFUND_QUANTITY_EXCEEDED);

        assertThat(successCount).isEqualTo(1);
        assertThat(rfFailCount).isEqualTo(1);
        assertThat(refundJpaRepository.count()).isEqualTo(1);
    }

    // ── 헬퍼 ──

    private static void awaitLatch(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private static long countByType(Object... results) {
        // overloaded for RefundResult
        return 0;
    }

    @SuppressWarnings("unchecked")
    private static <T> long countByType(Object r1, Object r2, Class<T> type) {
        long count = 0;
        if (type.isInstance(r1)) count++;
        if (type.isInstance(r2)) count++;
        return count;
    }

    private static long countByErrorCode(Object r1, Object r2, ErrorCode errorCode) {
        long count = 0;
        if (r1 instanceof BusinessException e && e.getErrorCode() == errorCode) count++;
        if (r2 instanceof BusinessException e && e.getErrorCode() == errorCode) count++;
        return count;
    }
}
