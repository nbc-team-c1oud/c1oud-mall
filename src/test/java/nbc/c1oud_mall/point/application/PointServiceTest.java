package nbc.c1oud_mall.point.application;

import jakarta.persistence.EntityManager;
import nbc.c1oud_mall.auth.application.Service.UserService;
import nbc.c1oud_mall.auth.domain.entity.User;
import nbc.c1oud_mall.auth.infrastructure.UserRepository;
import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.common.exception.ErrorCode;
import nbc.c1oud_mall.order.domain.Order;
import nbc.c1oud_mall.payment.domain.Payment;
import nbc.c1oud_mall.payment.domain.PaymentStatus;
import nbc.c1oud_mall.point.domain.PointHistory;
import nbc.c1oud_mall.point.domain.PointTransactionType;
import nbc.c1oud_mall.point.infrastructure.PointJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PointServiceTest {

    private static final Long USER_ID = 100L;
    private static final Long ORDER_ID = 10L;

    @Mock
    private PointJpaRepository pointJpaRepository;
    @Mock
    private UserService userService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private PointService pointService;

    private User user;
    private Payment payment;
    private Order orderRef;

    @BeforeEach
    void setUp() {
        user = new User("u@test.com", "pw", "tester", "01000000000");
        ReflectionTestUtils.setField(user, "id", USER_ID);
        ReflectionTestUtils.setField(user, "pointBalance", 5_000L);

        payment = Payment.rehydrate(
                42L, "portone-id", ORDER_ID, USER_ID,
                10_000L, 9_000L, 1_000L, 0L,
                PaymentStatus.PENDING, null, null);

        orderRef = mock(Order.class);
    }

    @Nested
    @DisplayName("deductPoints")
    class Deduct {

        @Test
        @DisplayName("정상: User 잔액 차감 + PointHistory(USE) 저장 + balanceAfter=잔액")
        void deduct_normal() {
            given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(user));
            given(entityManager.getReference(Order.class, ORDER_ID)).willReturn(orderRef);

            pointService.deductPoints(USER_ID, 1_000L, payment);

            assertThat(user.getPointBalance()).isEqualTo(4_000L);

            ArgumentCaptor<PointHistory> captor = ArgumentCaptor.forClass(PointHistory.class);
            verify(pointJpaRepository).save(captor.capture());
            PointHistory history = captor.getValue();
            assertThat(history.getUserId()).isEqualTo(USER_ID);
            assertThat(history.getOrder()).isSameAs(orderRef);
            assertThat(history.getPayment()).isSameAs(payment);
            assertThat(history.getAmount()).isEqualTo(1_000L);
            assertThat(history.getBalanceAfter()).isEqualTo(4_000L);
            assertThat(history.getTransactionType()).isEqualTo(PointTransactionType.USE);
        }

        @Test
        @DisplayName("PT002: 잔액 부족 → POINT_INSUFFICIENT, PointHistory 미저장")
        void deduct_insufficient_throws_pt002() {
            given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(user));

            assertThatThrownBy(() -> pointService.deductPoints(USER_ID, 10_000L, payment))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.POINT_INSUFFICIENT);

            assertThat(user.getPointBalance()).isEqualTo(5_000L);
            verify(pointJpaRepository, never()).save(any());
        }

        @Test
        @DisplayName("PT001: amount=0 → POINT_AMOUNT_INVALID")
        void deduct_zero_amount_throws_pt001() {
            given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(user));

            assertThatThrownBy(() -> pointService.deductPoints(USER_ID, 0L, payment))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.POINT_AMOUNT_INVALID);
        }

        @Test
        @DisplayName("PT001: amount=음수 → POINT_AMOUNT_INVALID")
        void deduct_negative_amount_throws_pt001() {
            given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(user));

            assertThatThrownBy(() -> pointService.deductPoints(USER_ID, -1L, payment))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.POINT_AMOUNT_INVALID);
        }

        @Test
        @DisplayName("U003: User 미존재 → USER_NOT_FOUND")
        void deduct_user_not_found() {
            given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> pointService.deductPoints(USER_ID, 1_000L, payment))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("User 비관적 락 조회 사용 (findByIdForUpdate)")
        void deduct_uses_pessimistic_lock_query() {
            given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(user));
            given(entityManager.getReference(Order.class, ORDER_ID)).willReturn(orderRef);

            pointService.deductPoints(USER_ID, 1_000L, payment);

            verify(userRepository).findByIdForUpdate(eq(USER_ID));
        }
    }

    @Nested
    @DisplayName("accruePoints")
    class Accrue {

        @Test
        @DisplayName("정상: User 잔액 증가 + PointHistory(EARN) 저장 + balanceAfter=잔액")
        void accrue_normal() {
            given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(user));
            given(entityManager.getReference(Order.class, ORDER_ID)).willReturn(orderRef);

            pointService.accruePoints(USER_ID, 200L, payment);

            assertThat(user.getPointBalance()).isEqualTo(5_200L);

            ArgumentCaptor<PointHistory> captor = ArgumentCaptor.forClass(PointHistory.class);
            verify(pointJpaRepository).save(captor.capture());
            PointHistory history = captor.getValue();
            assertThat(history.getUserId()).isEqualTo(USER_ID);
            assertThat(history.getAmount()).isEqualTo(200L);
            assertThat(history.getBalanceAfter()).isEqualTo(5_200L);
            assertThat(history.getTransactionType()).isEqualTo(PointTransactionType.EARN);
        }

        @Test
        @DisplayName("PT001: amount=0 → POINT_AMOUNT_INVALID, PointHistory 미저장")
        void accrue_zero_amount_throws_pt001() {
            given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(user));

            assertThatThrownBy(() -> pointService.accruePoints(USER_ID, 0L, payment))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.POINT_AMOUNT_INVALID);

            assertThat(user.getPointBalance()).isEqualTo(5_000L);
            verify(pointJpaRepository, never()).save(any());
        }

        @Test
        @DisplayName("U003: User 미존재 → USER_NOT_FOUND")
        void accrue_user_not_found() {
            given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> pointService.accruePoints(USER_ID, 200L, payment))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("restorePoints")
    class Restore {

        @Test
        @DisplayName("정상: User 잔액 증가 + PointHistory(USE_CANCEL) 저장 + balanceAfter=잔액")
        void restore_normal() {
            given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(user));
            given(entityManager.getReference(Order.class, ORDER_ID)).willReturn(orderRef);

            pointService.restorePoints(USER_ID, 1_000L, payment);

            assertThat(user.getPointBalance()).isEqualTo(6_000L);

            ArgumentCaptor<PointHistory> captor = ArgumentCaptor.forClass(PointHistory.class);
            verify(pointJpaRepository).save(captor.capture());
            PointHistory history = captor.getValue();
            assertThat(history.getUserId()).isEqualTo(USER_ID);
            assertThat(history.getOrder()).isSameAs(orderRef);
            assertThat(history.getPayment()).isSameAs(payment);
            assertThat(history.getAmount()).isEqualTo(1_000L);
            assertThat(history.getBalanceAfter()).isEqualTo(6_000L);
            assertThat(history.getTransactionType()).isEqualTo(PointTransactionType.USE_CANCEL);
        }

        @Test
        @DisplayName("PT001: amount=0 → POINT_AMOUNT_INVALID, PointHistory 미저장")
        void restore_zero_amount_throws_pt001() {
            given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(user));

            assertThatThrownBy(() -> pointService.restorePoints(USER_ID, 0L, payment))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.POINT_AMOUNT_INVALID);

            assertThat(user.getPointBalance()).isEqualTo(5_000L);
            verify(pointJpaRepository, never()).save(any());
        }

        @Test
        @DisplayName("U003: User 미존재 → USER_NOT_FOUND")
        void restore_user_not_found() {
            given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> pointService.restorePoints(USER_ID, 1_000L, payment))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("User 비관적 락 조회 사용 (findByIdForUpdate)")
        void restore_uses_pessimistic_lock_query() {
            given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(user));
            given(entityManager.getReference(Order.class, ORDER_ID)).willReturn(orderRef);

            pointService.restorePoints(USER_ID, 1_000L, payment);

            verify(userRepository).findByIdForUpdate(eq(USER_ID));
        }
    }
}
